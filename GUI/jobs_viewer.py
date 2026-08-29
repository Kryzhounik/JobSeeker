from __future__ import annotations

import json
import queue
import re
import sqlite3
import subprocess
import sys
import textwrap
import threading
import time
import tkinter as tk
import urllib.error
import urllib.request
import webbrowser
from datetime import datetime
from pathlib import Path
from tkinter import messagebox
from tkinter import ttk
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "Data" / "jobs.sqlite"
SETTINGS_PATH = Path(__file__).with_name("jobs_viewer_settings.json")
AVAILABILITY_LOG_PATH = Path(__file__).with_name("linkedin_availability_check.log")
BLOCKED_TITLES_PATH = (
    ROOT
    / "Driver"
    / "collector"
    / "filtering"
    / "linkedin_preview_blocked_titles.txt"
)
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
DRIVER_ROOT = ROOT / "Driver"
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.applications import (
    create_for_source_urls as create_applications_for_source_urls,
    list_applications as load_applications,
    list_application_statuses,
    update_status as update_application_status,
)
from db.companies import (
    create_company,
    update_company_linkedin_id,
    update_company_priority,
)
from db.job_mapper import delete_jobs
from db.migrate import migrate_database
from Tools.filter_database import collect_rejected_jobs


JOB_COLUMNS = (
    ("score", "Score", 56, "center"),
    ("fit", "Fit", 48, "center"),
    ("interest", "Interest", 72, "center"),
    ("status", "Status", 72, "center"),
    ("remote_scope", "Remote", 92, "w"),
    ("relocation", "Reloc", 72, "center"),
    ("location", "Location", 150, "w"),
    ("company", "Company", 160, "w"),
    ("title", "Title", 280, "w"),
    ("role", "Role", 120, "w"),
    ("seniority", "Seniority", 100, "w"),
    ("primary_language", "Language", 140, "w"),
    ("salary", "Salary", 150, "w"),
    ("added_at", "Added", 110, "w"),
    ("candidate_fit_reason_code", "Reason code", 130, "w"),
    ("candidate_fit_reason", "Reason", 360, "w"),
)

DETAIL_FIELDS = (
    ("title", "Title"),
    ("company", "Company"),
    ("id", "ID"),
    ("source_job_id", "Source ID"),
    ("score", "Score"),
    ("fit", "Fit"),
    ("interest", "Interest"),
    ("status", "Status"),
    ("role", "Role"),
    ("seniority", "Seniority"),
    ("location", "Location"),
    ("remote_scope", "Remote scope"),
    ("remote_type", "Remote type"),
    ("relocation", "Relocation"),
    ("salary", "Salary"),
    ("languages", "Languages"),
    ("added_at", "Added"),
)

TECH_COLUMNS = (
    ("technology", "Technology", 240, "w"),
    ("req", "Req", 72, "center"),
    ("level", "Level", 140, "w"),
    ("raw_value", "Raw", 280, "w"),
)

SCORE_EDIT_FIELDS = {"fit", "interest"}
JOB_NUMERIC_COLUMNS = {"score", "fit", "interest"}
TECH_NUMERIC_COLUMNS = {"level"}
DEFAULT_STATUS_VALUES = ("New", "Checked", "Postponed", "Applied", "Closed")
LINKEDIN_CHECK_DELAY_SECONDS = 5
LINKEDIN_CHECK_TIMEOUT_SECONDS = 20
LINKEDIN_CLOSED_MARKER = "no longer accepting applications"
LINKEDIN_JOB_ID_PATTERN = re.compile(
    r"(?:/jobs/view/|/jobPosting/|currentJobId=)(\d+)"
)
READONLY_FIELD_COLORS = {
    "background": "#f4f4f0",
    "foreground": "#303030",
    "highlightbackground": "#a9a9a9",
    "highlightcolor": "#a9a9a9",
}
EDITABLE_FIELD_COLORS = {
    "background": "#fff3b0",
    "foreground": "#000000",
    "insertbackground": "#000000",
    "highlightbackground": "#000000",
    "highlightcolor": "#000000",
}
LEVEL_SORT_VALUES = {
    "": 0,
    "nice to have": 1,
    "junior": 2,
    "regular": 3,
    "advanced": 4,
    "master": 5,
}
DATABASE_DATE_FORMAT = "%Y-%m-%d"
DISPLAY_DATE_FORMAT = "%d.%m.%Y"


def db_uri() -> str:
    return f"{DB_PATH.as_uri()}?mode=ro"


def ensure_database_schema() -> None:
    migrate_database(db_path=DB_PATH)


def clean(value: Any) -> str:
    if value is None:
        return ""
    return str(value)


def format_company_display(name: Any, application_count: Any) -> str:
    company = clean(name)
    if not company:
        return ""
    try:
        count = int(application_count)
    except (TypeError, ValueError):
        count = 0
    return f"{company} ({count})"


def append_unique_blacklist_term(path: Path, value: Any) -> bool:
    term = " ".join(clean(value).split())
    if not term:
        raise ValueError("Select a non-empty title fragment.")

    existing_text = path.read_text(encoding="utf-8") if path.exists() else ""
    existing_terms = {
        line.strip().casefold()
        for line in existing_text.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    }
    if term.casefold() in existing_terms:
        return False

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as blacklist_file:
        if existing_text and not existing_text.endswith(("\n", "\r")):
            blacklist_file.write("\n")
        blacklist_file.write(term + "\n")
    return True


def format_display_date(value: Any) -> str:
    text = clean(value).strip()
    match = re.match(r"\d{4}-\d{2}-\d{2}", text)
    if match is None:
        return text
    try:
        parsed = datetime.strptime(match.group(0), DATABASE_DATE_FORMAT)
    except ValueError:
        return text
    return parsed.strftime(DISPLAY_DATE_FORMAT)


def parse_display_date(value: Any) -> datetime:
    text = clean(value).strip()
    parsed = datetime.strptime(text, DISPLAY_DATE_FORMAT)
    if parsed.strftime(DISPLAY_DATE_FORMAT) != text:
        raise ValueError(text)
    return parsed


def calculate_score(fit: int, interest: int) -> int:
    return (interest * fit * fit + 5000) // 10000


class CopyableText(tk.Text):
    def __init__(
        self,
        parent: tk.Misc,
        *,
        readonly: bool = True,
        copy_callback: Any = None,
        **kwargs: Any,
    ) -> None:
        super().__init__(parent, **kwargs)
        self.readonly = readonly
        self.copy_callback = copy_callback

        self.bind("<Control-a>", self._select_all)
        self.bind("<Control-A>", self._select_all)
        self.bind("<Control-c>", self._copy_selection)
        self.bind("<Control-C>", self._copy_selection)
        self.bind("<Control-Insert>", self._copy_selection)
        self.bind("<Control-KeyPress>", self._control_keypress)
        self.bind("<<Copy>>", self._copy_selection)
        self.bind("<Button-3>", self._show_context_menu)

        if readonly:
            self.bind("<<Cut>>", self._break_event)
            self.bind("<<Paste>>", self._break_event)
            self.bind("<KeyPress>", self._block_edit)

    def set_value(self, value: Any) -> None:
        self.delete("1.0", tk.END)
        text = clean(value)
        if text:
            self.insert("1.0", text)
        self.mark_set(tk.INSERT, "1.0")

    def get_value(self) -> str:
        return self.get("1.0", "end-1c")

    def selected_text(self) -> str:
        try:
            return self.get(tk.SEL_FIRST, tk.SEL_LAST)
        except tk.TclError:
            return ""

    def _copy_selection(self, _event: tk.Event[tk.Misc] | None = None) -> str:
        text = self.selected_text()
        if text:
            self.clipboard_clear()
            self.clipboard_append(text)
            self.update_idletasks()
            if self.copy_callback is not None:
                self.copy_callback()
        return "break"

    def _select_all(self, _event: tk.Event[tk.Misc] | None = None) -> str:
        self.tag_add(tk.SEL, "1.0", "end-1c")
        self.mark_set(tk.INSERT, "1.0")
        self.see(tk.INSERT)
        return "break"

    def _control_keypress(self, event: tk.Event[tk.Misc]) -> str | None:
        key = event.keysym.lower()
        keycode = int(getattr(event, "keycode", 0) or 0)
        if key == "a" or keycode == 65:
            return self._select_all()
        if key in {"c", "insert"} or keycode in {45, 67}:
            return self._copy_selection()
        return None

    def _show_context_menu(self, event: tk.Event[tk.Misc]) -> str:
        menu = tk.Menu(self, tearoff=False)
        if not self.readonly:
            menu.add_command(label="Cut", command=lambda: self.event_generate("<<Cut>>"))
        menu.add_command(label="Copy", command=self._copy_selection)
        if not self.readonly:
            menu.add_command(
                label="Paste",
                command=lambda: self.event_generate("<<Paste>>"),
            )
        menu.add_command(label="Select all", command=self._select_all)
        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()
        return "break"

    def _block_edit(self, event: tk.Event[tk.Misc]) -> str | None:
        key = event.keysym.lower()
        if event.state & 0x4:
            return None
        if key in {
            "left",
            "right",
            "up",
            "down",
            "home",
            "end",
            "prior",
            "next",
            "tab",
            "shift_l",
            "shift_r",
            "control_l",
            "control_r",
        }:
            return None
        return "break"

    def _break_event(self, _event: tk.Event[tk.Misc]) -> str:
        return "break"


class SortableTableMixin:
    def _init_sorting(
        self,
        columns: tuple[tuple[str, str, int, str], ...],
        numeric_columns: set[str] | None = None,
        sort_column: str | None = None,
        sort_descending: bool = False,
    ) -> None:
        self.sortable_columns = columns
        self.sortable_numeric_columns = numeric_columns or set()
        self.sort_column = sort_column
        self.sort_descending = sort_descending

    def _sort_direction(self, column: str) -> bool:
        if self.sort_column == column:
            return not self.sort_descending
        return column in self.sortable_numeric_columns

    def _sort_value(self, column: str, value: Any) -> Any:
        text = clean(value).strip()
        if column == "level":
            return LEVEL_SORT_VALUES.get(text.casefold(), 0)
        if column in {"added_at", "applied_at"}:
            try:
                return parse_display_date(text)
            except ValueError:
                return datetime.min
        if column in self.sortable_numeric_columns:
            try:
                return float(value)
            except (TypeError, ValueError):
                return float("-inf")
        return text.casefold()

    def _sort_heading_text(self, name: str, label: str) -> str:
        if name != self.sort_column:
            return label
        marker = " v" if self.sort_descending else " ^"
        return f"{label}{marker}"


class SortableRows(SortableTableMixin):
    def __init__(
        self,
        columns: tuple[tuple[str, str, int, str], ...],
        numeric_columns: set[str] | None = None,
        sort_column: str | None = None,
        sort_descending: bool = False,
    ) -> None:
        self._init_sorting(
            columns,
            numeric_columns,
            sort_column,
            sort_descending,
        )

    def sort_by(self, column: str) -> None:
        valid = {
            name for name, _label, _width, _anchor in self.sortable_columns
        }
        if column not in valid:
            return
        self.sort_descending = self._sort_direction(column)
        self.sort_column = column

    def sorted_rows(self, rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
        result = list(rows)
        if not self.sort_column:
            return result
        column = self.sort_column
        result.sort(
            key=lambda row: self._sort_value(column, row.get(column, "")),
            reverse=self.sort_descending,
        )
        return result


class SortableTreeview(SortableTableMixin, ttk.Treeview):
    def __init__(
        self,
        parent: tk.Misc,
        columns: tuple[tuple[str, str, int, str], ...],
        *,
        numeric_columns: set[str] | None = None,
        sort_column: str | None = None,
        sort_descending: bool = False,
        on_sorted: Any = None,
        **kwargs: Any,
    ) -> None:
        names = tuple(name for name, _label, _width, _anchor in columns)
        super().__init__(parent, columns=names, show="headings", **kwargs)
        self._init_sorting(
            columns,
            numeric_columns,
            sort_column,
            sort_descending,
        )
        self.on_sorted = on_sorted
        for name, label, width, anchor in columns:
            self.heading(
                name,
                text=self._sort_heading_text(name, label),
                command=lambda value=name: self.sort_by(value),
            )
            self.column(
                name,
                width=width,
                minwidth=42,
                anchor=anchor,
                stretch=True,
            )

    def sort_by(
        self,
        column: str,
        descending: bool | None = None,
        *,
        notify: bool = True,
    ) -> None:
        valid = {name for name, _label, _width, _anchor in self.sortable_columns}
        if column not in valid:
            return
        if descending is None:
            descending = self._sort_direction(column)
        self.sort_column = column
        self.sort_descending = bool(descending)
        items = list(self.get_children(""))
        items.sort(
            key=lambda item: self._sort_value(column, self.set(item, column)),
            reverse=self.sort_descending,
        )
        for index, item in enumerate(items):
            self.move(item, "", index)
            self.item(item, tags=("odd",) if index % 2 else ())
        self._update_sort_headings()
        if notify and self.on_sorted is not None:
            self.on_sorted(self.sort_column, self.sort_descending)

    def reapply_sort(self) -> None:
        if self.sort_column:
            self.sort_by(self.sort_column, self.sort_descending, notify=False)
        else:
            self._update_sort_headings()

    def _update_sort_headings(self) -> None:
        for name, label, _width, _anchor in self.sortable_columns:
            self.heading(
                name,
                text=self._sort_heading_text(name, label),
                command=lambda value=name: self.sort_by(value),
            )


class CopyableGrid(SortableTableMixin, ttk.Frame):
    def __init__(
        self,
        parent: tk.Misc,
        columns: tuple[tuple[str, str, int, str], ...],
        *,
        numeric_columns: set[str] | None = None,
        copy_callback: Any = None,
    ) -> None:
        super().__init__(parent)
        self.columns = columns
        self._init_sorting(columns, numeric_columns)
        self.copy_callback = copy_callback
        self.headers: dict[str, tk.Label] = {}
        self.cells: list[CopyableText] = []
        self.rows: list[dict[str, str]] = []

        self.columnconfigure(0, weight=1)
        self.rowconfigure(0, weight=1)

        self.canvas = tk.Canvas(self, highlightthickness=0, borderwidth=0)
        self.canvas.grid(row=0, column=0, sticky="nsew")
        vertical_scroll = ttk.Scrollbar(
            self,
            orient=tk.VERTICAL,
            command=self.canvas.yview,
        )
        vertical_scroll.grid(row=0, column=1, sticky="ns")
        horizontal_scroll = ttk.Scrollbar(
            self,
            orient=tk.HORIZONTAL,
            command=self.canvas.xview,
        )
        horizontal_scroll.grid(row=1, column=0, sticky="ew")
        self.canvas.configure(
            yscrollcommand=vertical_scroll.set,
            xscrollcommand=horizontal_scroll.set,
        )

        self.rows_frame = tk.Frame(self.canvas, background="#ffffff")
        self.canvas_window = self.canvas.create_window(
            (0, 0),
            window=self.rows_frame,
            anchor="nw",
        )
        self.rows_frame.bind("<Configure>", self._update_scroll_region)
        self.canvas.bind("<Configure>", self._fit_rows_to_canvas)

        for column_index, (name, label, width, anchor) in enumerate(columns):
            self.rows_frame.grid_columnconfigure(
                column_index,
                minsize=width,
                weight=max(1, width),
            )
            header = tk.Label(
                self.rows_frame,
                text=label,
                anchor=anchor,
                background="#e7e9e7",
                foreground="#202020",
                font=("Segoe UI", 9, "bold"),
                borderwidth=1,
                relief="solid",
                padx=4,
                pady=3,
                cursor="hand2",
            )
            header.grid(row=0, column=column_index, sticky="nsew")
            header.bind(
                "<Button-1>",
                lambda _event, column=name: self.sort_by(column),
            )
            self._bind_canvas_wheel(header)
            self.headers[name] = header

    def set_rows(self, rows: list[dict[str, str]]) -> None:
        self.rows = list(rows)
        if self.sort_column:
            self.rows.sort(
                key=lambda row: self._sort_value(
                    self.sort_column or "",
                    row.get(self.sort_column or "", ""),
                ),
                reverse=self.sort_descending,
            )
        self._render_rows()

    def _render_rows(self) -> None:
        for cell in self.cells:
            cell.destroy()
        self.cells.clear()

        for row_index, row in enumerate(self.rows, start=1):
            background = "#f7f9fb" if row_index % 2 == 0 else "#ffffff"
            for column_index, (name, _label, width, anchor) in enumerate(self.columns):
                cell = CopyableText(
                    self.rows_frame,
                    readonly=True,
                    copy_callback=self.copy_callback,
                    width=max(6, width // 8),
                    height=1,
                    wrap="none",
                    borderwidth=0,
                    relief="flat",
                    background=background,
                    foreground="#202020",
                    font=("Segoe UI", 9),
                    padx=4,
                    pady=3,
                    cursor="xterm",
                )
                cell.tag_configure("value", justify=self._text_justify(anchor))
                cell.insert("1.0", clean(row.get(name, "")), "value")
                cell.grid(
                    row=row_index,
                    column=column_index,
                    sticky="nsew",
                    padx=1,
                    pady=1,
                )
                self._bind_canvas_wheel(cell)
                self.cells.append(cell)

        self.rows_frame.update_idletasks()
        self._update_scroll_region()

    def sort_by(self, column: str) -> None:
        valid = {name for name, _label, _width, _anchor in self.columns}
        if column not in valid:
            return
        descending = self._sort_direction(column)
        self.sort_column = column
        self.sort_descending = descending
        self.rows.sort(
            key=lambda row: self._sort_value(column, row.get(column, "")),
            reverse=descending,
        )
        self._render_rows()
        self.update_headings(self.sort_column, self.sort_descending)

    def reset_sort(self) -> None:
        self.sort_column = None
        self.sort_descending = False
        self.update_headings(None, False)

    def update_headings(
        self,
        sort_column: str | None,
        descending: bool,
    ) -> None:
        self.sort_column = sort_column
        self.sort_descending = descending
        for name, label, _width, _anchor in self.columns:
            self.headers[name].configure(text=self._sort_heading_text(name, label))

    def _text_justify(self, anchor: str) -> str:
        if anchor in {"center", "e", "right"}:
            return "center" if anchor == "center" else "right"
        return "left"

    def _update_scroll_region(
        self,
        _event: tk.Event[tk.Misc] | None = None,
    ) -> None:
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _fit_rows_to_canvas(self, event: tk.Event[tk.Misc]) -> None:
        requested_width = self.rows_frame.winfo_reqwidth()
        self.canvas.itemconfigure(
            self.canvas_window,
            width=max(event.width, requested_width),
        )

    def _bind_canvas_wheel(self, widget: tk.Misc) -> None:
        widget.bind("<MouseWheel>", self._scroll_vertical)
        widget.bind("<Shift-MouseWheel>", self._scroll_horizontal)
        widget.bind("<Button-4>", self._scroll_vertical)
        widget.bind("<Button-5>", self._scroll_vertical)

    def _scroll_vertical(self, event: tk.Event[tk.Misc]) -> str:
        if getattr(event, "num", None) == 4:
            direction = -1
        elif getattr(event, "num", None) == 5:
            direction = 1
        else:
            direction = -1 if event.delta > 0 else 1
        self.canvas.yview_scroll(direction, "units")
        return "break"

    def _scroll_horizontal(self, event: tk.Event[tk.Misc]) -> str:
        direction = -1 if event.delta > 0 else 1
        self.canvas.xview_scroll(direction, "units")
        return "break"


class JobsViewer(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.settings = self._load_settings()
        self.title("Seeker Jobs")
        self.geometry(self._saved_window_size("main", "1280x820", 980, 650))
        self.minsize(980, 650)
        self._main_size_save_after_id: str | None = None

        self.job_rows: dict[str, dict[str, str]] = {}
        self.current_source_url = ""
        self.current_status = ""
        self.current_fit = ""
        self.current_interest = ""
        self.detail_fields: dict[str, CopyableText] = {}
        self.status_buttons: list[ttk.Button] = []
        self.availability_button: ttk.Button | None = None
        self.refilter_button: ttk.Button | None = None
        self.refilter_detail_button: ttk.Button | None = None
        self.config_window: tk.Toplevel | None = None
        self.config_vars: dict[int, tk.BooleanVar] = {}
        self.companies_window: tk.Toplevel | None = None
        self.company_rows: list[dict[str, Any]] = []
        self.company_rows_by_item: dict[str, dict[str, Any]] = {}
        self.company_checkbox_widgets: list[tk.Widget] = []
        self.company_controls_after_id: str | None = None
        self.company_linkedin_editor: ttk.Entry | None = None
        self.company_linkedin_editor_item = ""
        self.company_sort_column = self._saved_company_sort_column()
        self.company_sort_descending = self._saved_company_sort_descending()
        self.selected_company_id: int | None = None
        self.applications_window: tk.Toplevel | None = None
        self.application_rows: list[dict[str, Any]] = []
        self.application_rows_by_item: dict[str, dict[str, Any]] = {}
        self.application_link_labels: list[tk.Label] = []
        self.application_links_after_id: str | None = None
        self.application_status_editor: ttk.Combobox | None = None
        self.application_sort_column = self._saved_application_sort_column()
        self.application_sort_descending = self._saved_application_sort_descending()
        self.job_link_labels: list[tk.Label] = []
        self.job_links_after_id: str | None = None
        self.job_column_resize_active = False
        self.title_selection_entry: tk.Entry | None = None
        self.title_selection_item = ""
        self.title_selection_menu_open = False
        self.availability_check_running = False
        self.availability_queue: queue.SimpleQueue[tuple[str, Any]] = queue.SimpleQueue()
        self.availability_log_lock = threading.Lock()
        self.refilter_running = False
        self.refilter_queue: queue.SimpleQueue[tuple[str, Any]] = queue.SimpleQueue()
        self.status_values = self._available_status_values()
        self.status_filter_vars = {
            status: tk.BooleanVar(value=self._saved_status_filter_value(status))
            for status in self.status_values
        }
        self.show_zero_var = tk.BooleanVar(
            value=bool(self.settings.get("show_zero", False))
        )
        self.id_search_var = tk.StringVar(value="")
        self.added_from_var = tk.StringVar(value="")
        self.reason_filter_var = tk.StringVar(value="")
        self.job_sort_column = self._saved_job_sort_column()
        self.job_sort_descending = self._saved_job_sort_descending()
        self.tech_rows: list[dict[str, str]] = []
        self.detail_view_mode = "skills"

        self._configure_style()
        self._build_ui()
        self._bind_global_copy_shortcuts()
        self.bind("<Configure>", self._schedule_main_window_size_save)
        self.bind("<Destroy>", self._cancel_main_window_size_save, add="+")
        self.protocol("WM_DELETE_WINDOW", self._close_app)
        self.refresh_jobs()

    def _configure_style(self) -> None:
        style = ttk.Style(self)
        if "clam" in style.theme_names():
            style.theme_use("clam")
        style.configure("Treeview", rowheight=24)
        style.configure("Treeview.Heading", font=("Segoe UI", 9, "bold"))
        style.configure("Muted.TLabel", foreground="#606a76")
        style.configure("Title.TLabel", font=("Segoe UI", 13, "bold"))

    def _build_ui(self) -> None:
        self.columnconfigure(0, weight=1)
        self.rowconfigure(1, weight=1)
        self.status_var = tk.StringVar(value="")

        toolbar = ttk.Frame(self, padding=(10, 8))
        toolbar.grid(row=0, column=0, sticky="ew")
        toolbar.columnconfigure(5, weight=1)

        refresh_button = ttk.Button(toolbar, text="Refresh", command=self.refresh_jobs)
        refresh_button.grid(row=0, column=0, sticky="w")

        self.availability_button = ttk.Button(
            toolbar,
            text="Check LinkedIn",
            command=self._start_linkedin_availability_check,
        )
        self.availability_button.grid(row=0, column=1, sticky="w", padx=(6, 0))

        self.refilter_button = ttk.Button(
            toolbar,
            text="Refilter",
            command=self._start_refilter_database,
        )
        self.refilter_button.grid(row=0, column=2, sticky="w", padx=(6, 0))

        ttk.Button(
            toolbar,
            text="Companies",
            command=self._open_companies_window,
        ).grid(row=0, column=3, sticky="w", padx=(6, 0))

        ttk.Button(
            toolbar,
            text="Applications",
            command=self._open_applications_window,
        ).grid(row=0, column=4, sticky="w", padx=(6, 0))

        filters = ttk.Frame(toolbar)
        filters.grid(row=0, column=5, sticky="w", padx=(8, 8))

        ttk.Label(filters, text="Status", style="Muted.TLabel").grid(
            row=0,
            column=0,
            sticky="w",
            padx=(0, 5),
        )
        for column_index, status in enumerate(self.status_values, start=1):
            ttk.Checkbutton(
                filters,
                text=status,
                variable=self.status_filter_vars[status],
                command=self._filter_changed,
            ).grid(row=0, column=column_index, sticky="w", padx=(0, 4))

        ttk.Checkbutton(
            filters,
            text="Show zero",
            variable=self.show_zero_var,
            command=self._filter_changed,
        ).grid(row=0, column=len(self.status_values) + 1, sticky="w", padx=(8, 0))

        search_filters = ttk.Frame(toolbar)
        search_filters.grid(row=0, column=6, sticky="e")

        ttk.Label(search_filters, text="ID", style="Muted.TLabel").grid(
            row=0,
            column=0,
            sticky="w",
            padx=(0, 4),
        )
        id_entry = ttk.Entry(search_filters, textvariable=self.id_search_var, width=13)
        id_entry.grid(row=0, column=1, sticky="w")
        self._bind_editable_entry(id_entry)
        id_entry.bind("<Return>", self._refresh_from_event)

        ttk.Label(search_filters, text="Date", style="Muted.TLabel").grid(
            row=0,
            column=2,
            sticky="w",
            padx=(10, 4),
        )
        self.added_from_entry = ttk.Entry(
            search_filters,
            textvariable=self.added_from_var,
            width=12,
        )
        self.added_from_entry.grid(row=0, column=3, sticky="w")
        self._bind_editable_entry(self.added_from_entry)
        self.added_from_entry.bind("<Return>", self._refresh_from_event)

        ttk.Label(search_filters, text="Reason", style="Muted.TLabel").grid(
            row=0,
            column=4,
            sticky="w",
            padx=(10, 4),
        )
        self.reason_filter_entry = ttk.Entry(
            search_filters,
            textvariable=self.reason_filter_var,
            width=20,
        )
        self.reason_filter_entry.grid(row=0, column=5, sticky="w")
        self._bind_editable_entry(self.reason_filter_entry)
        self.reason_filter_entry.bind("<Return>", self._refresh_from_event)

        ttk.Button(search_filters, text="Search", command=self.refresh_jobs).grid(
            row=0,
            column=6,
            sticky="w",
            padx=(4, 0),
        )
        ttk.Button(
            search_filters,
            text="Clear",
            command=self._clear_search_filters,
        ).grid(
            row=0,
            column=7,
            sticky="w",
            padx=(4, 0),
        )
        ttk.Button(
            search_filters,
            text="\u2699",
            width=3,
            command=self._open_config_window,
        ).grid(
            row=0,
            column=8,
            sticky="e",
            padx=(8, 0),
        )

        pane = ttk.PanedWindow(self, orient=tk.VERTICAL)
        pane.grid(row=1, column=0, sticky="nsew", padx=10, pady=(0, 10))

        jobs_frame = ttk.Frame(pane)
        detail_frame = ttk.Frame(pane)
        pane.add(jobs_frame, weight=3)
        pane.add(detail_frame, weight=2)

        self._build_jobs_table(jobs_frame)
        self._build_detail(detail_frame)

        footer = ttk.Frame(self, padding=(10, 0, 10, 10))
        footer.grid(row=2, column=0, sticky="ew")
        footer.columnconfigure(2, weight=1)
        self.refilter_detail_button = ttk.Button(
            footer,
            text="Refilter detail",
            command=self._start_refilter_detail,
        )
        self.refilter_detail_button.grid(row=0, column=0, sticky="w")
        ttk.Button(
            footer,
            text="Black titles",
            command=self._open_black_titles,
        ).grid(row=0, column=1, sticky="w", padx=(6, 0))
        ttk.Label(footer, textvariable=self.status_var, style="Muted.TLabel").grid(
            row=0,
            column=2,
            sticky="e",
        )

    def _build_jobs_table(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(0, weight=1)

        self.jobs_tree = SortableTreeview(
            parent,
            JOB_COLUMNS,
            numeric_columns=JOB_NUMERIC_COLUMNS,
            sort_column=self.job_sort_column,
            sort_descending=self.job_sort_descending,
            on_sorted=self._jobs_tree_sorted,
            selectmode="extended",
        )
        self.jobs_tree.grid(row=0, column=0, sticky="nsew")

        y_scroll = ttk.Scrollbar(
            parent,
            orient=tk.VERTICAL,
            command=self._jobs_tree_yview,
        )
        y_scroll.grid(row=0, column=1, sticky="ns")
        x_scroll = ttk.Scrollbar(
            parent,
            orient=tk.HORIZONTAL,
            command=self._jobs_tree_xview,
        )
        x_scroll.grid(row=1, column=0, sticky="ew")
        self.jobs_tree.configure(yscrollcommand=y_scroll.set, xscrollcommand=x_scroll.set)

        self.jobs_tree.tag_configure("odd", background="#f7f9fb")
        self.jobs_tree.bind("<<TreeviewSelect>>", self._on_job_selected)
        self.jobs_tree.bind("<Control-c>", self._copy_tree_selection)
        self.jobs_tree.bind("<Control-C>", self._copy_tree_selection)
        self.jobs_tree.bind("<Control-Insert>", self._copy_tree_selection)
        self.jobs_tree.bind("<<Copy>>", self._copy_tree_selection)
        self.jobs_tree.bind("<Button-3>", self._show_copy_menu)
        self.jobs_tree.bind("<Double-Button-1>", self._start_title_selection)
        self.jobs_tree.bind(
            "<ButtonPress-1>",
            self._jobs_tree_button_press,
            add="+",
        )
        self.jobs_tree.bind(
            "<B1-Motion>",
            self._jobs_tree_button_drag,
            add="+",
        )
        self.jobs_tree.bind(
            "<ButtonRelease-1>",
            self._jobs_tree_button_release,
            add="+",
        )
        self.jobs_tree.bind(
            "<Configure>",
            self._jobs_tree_configured,
            add="+",
        )
        self.jobs_tree.bind(
            "<MouseWheel>",
            self._jobs_tree_mousewheel,
            add="+",
        )

    def _build_detail(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(1, weight=1)

        header = ttk.Frame(parent, padding=(0, 8, 0, 6))
        header.grid(row=0, column=0, sticky="ew")
        header.columnconfigure(0, weight=1)

        link_frame = ttk.Frame(header)
        link_frame.grid(row=0, column=0, sticky="ew")
        link_frame.columnconfigure(0, weight=1)

        self.link_text = CopyableText(
            link_frame,
            readonly=True,
            copy_callback=lambda: self.status_var.set("Copied"),
            height=1,
            wrap="none",
            borderwidth=1,
            relief="solid",
            padx=4,
            pady=2,
        )
        self.link_text.grid(row=0, column=0, sticky="ew")

        ttk.Button(link_frame, text="Open", command=self._open_current_link).grid(
            row=0,
            column=1,
            sticky="e",
            padx=(8, 0),
        )

        status_frame = ttk.Frame(header)
        status_frame.grid(row=1, column=0, sticky="ew", pady=(6, 0))
        ttk.Label(status_frame, text="Set status", style="Muted.TLabel").grid(
            row=0,
            column=0,
            sticky="w",
            padx=(0, 8),
        )
        for column_index, status in enumerate(self.status_values, start=1):
            button = ttk.Button(
                status_frame,
                text=status,
                command=lambda value=status: self._set_current_status(value),
            )
            button.grid(row=0, column=column_index, sticky="w", padx=(0, 6))
            self.status_buttons.append(button)
        self._set_status_buttons_state(False)

        self.detail_view_button = ttk.Button(
            status_frame,
            text="Text",
            width=7,
            command=self._toggle_detail_view,
        )

        def position_detail_view_button(
            _event: tk.Event[tk.Misc] | None = None,
        ) -> None:
            last_status_button = self.status_buttons[-1]
            controls_right = (
                last_status_button.winfo_x()
                + last_status_button.winfo_width()
                + 18
            )
            self.detail_view_button.place(
                x=max(controls_right, int(status_frame.winfo_width() * 0.33)),
                y=0,
            )

        status_frame.bind("<Configure>", position_detail_view_button)
        status_frame.after_idle(position_detail_view_button)

        body = ttk.PanedWindow(parent, orient=tk.HORIZONTAL)
        body.grid(row=1, column=0, sticky="nsew")

        meta_frame = ttk.Frame(body, padding=(0, 0, 8, 0))
        tech_frame = ttk.Frame(body, padding=(8, 0, 0, 0))
        body.add(meta_frame, weight=1)
        body.add(tech_frame, weight=3)

        self._build_meta(meta_frame)
        self._build_tech_and_summary(tech_frame)

        def set_initial_split() -> None:
            width = body.winfo_width()
            if width > 1:
                body.sashpos(0, max(360, int(width * 0.33)))

        self.after(100, set_initial_split)

    def _build_meta(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(1, weight=1)

        for row_index, (name, label) in enumerate(DETAIL_FIELDS):
            ttk.Label(parent, text=label, style="Muted.TLabel").grid(
                row=row_index,
                column=0,
                sticky="nw",
                padx=(0, 10),
                pady=3,
            )
            field = CopyableText(
                parent,
                readonly=name not in SCORE_EDIT_FIELDS,
                copy_callback=lambda: self.status_var.set("Copied"),
                height=1,
                wrap="none",
                borderwidth=1,
                relief="solid",
                highlightthickness=1,
                padx=4,
                pady=2,
            )
            field.grid(
                row=row_index,
                column=1,
                sticky="ew",
                pady=3,
            )
            if name in SCORE_EDIT_FIELDS:
                field.configure(
                    borderwidth=2,
                    highlightthickness=2,
                    **EDITABLE_FIELD_COLORS,
                )
                self._bind_score_field(field)
            else:
                field.configure(**READONLY_FIELD_COLORS)
            self.detail_fields[name] = field

    def _build_tech_and_summary(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(0, weight=1)

        self.skills_view_frame = ttk.Frame(parent)
        self.skills_view_frame.grid(row=0, column=0, sticky="nsew")
        self.skills_view_frame.columnconfigure(0, weight=1)
        self.skills_view_frame.rowconfigure(0, weight=3)
        self.skills_view_frame.rowconfigure(2, weight=2)

        tech_box = ttk.Frame(self.skills_view_frame)
        tech_box.grid(row=0, column=0, sticky="nsew")
        tech_box.columnconfigure(0, weight=1)
        tech_box.rowconfigure(0, weight=1)

        self.tech_table = CopyableGrid(
            tech_box,
            TECH_COLUMNS,
            numeric_columns=TECH_NUMERIC_COLUMNS,
            copy_callback=lambda: self.status_var.set("Copied"),
        )
        self.tech_table.grid(row=0, column=0, sticky="nsew")

        ttk.Label(
            self.skills_view_frame,
            text="Summary",
            style="Muted.TLabel",
        ).grid(
            row=1,
            column=0,
            sticky="w",
            pady=(8, 3),
        )

        self.summary_text = CopyableText(
            self.skills_view_frame,
            readonly=True,
            copy_callback=lambda: self.status_var.set("Copied"),
            height=8,
            wrap="word",
            borderwidth=1,
            relief="solid",
            padx=8,
            pady=6,
        )
        self.summary_text.grid(row=2, column=0, sticky="nsew")

        self.readable_text_frame = ttk.Frame(parent)
        self.readable_text_frame.grid(row=0, column=0, sticky="nsew")
        self.readable_text_frame.columnconfigure(0, weight=1)
        self.readable_text_frame.rowconfigure(0, weight=1)

        self.readable_text = CopyableText(
            self.readable_text_frame,
            readonly=True,
            copy_callback=lambda: self.status_var.set("Copied"),
            wrap="word",
            borderwidth=1,
            relief="solid",
            padx=8,
            pady=6,
        )
        self.readable_text.grid(row=0, column=0, sticky="nsew")
        readable_scroll = ttk.Scrollbar(
            self.readable_text_frame,
            orient=tk.VERTICAL,
            command=self.readable_text.yview,
        )
        readable_scroll.grid(row=0, column=1, sticky="ns")
        self.readable_text.configure(yscrollcommand=readable_scroll.set)
        self.readable_text_frame.grid_remove()

    def _toggle_detail_view(self) -> None:
        if self.detail_view_mode == "skills":
            self.skills_view_frame.grid_remove()
            self.readable_text_frame.grid()
            self.detail_view_mode = "text"
            self.detail_view_button.configure(text="Skills")
            return

        self.readable_text_frame.grid_remove()
        self.skills_view_frame.grid()
        self.detail_view_mode = "skills"
        self.detail_view_button.configure(text="Text")

    def connect(self) -> sqlite3.Connection:
        if not DB_PATH.exists():
            raise FileNotFoundError(f"Database not found: {DB_PATH}")
        ensure_database_schema()
        connection = sqlite3.connect(db_uri(), uri=True)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA query_only = ON")
        return connection

    def _available_status_values(self) -> tuple[str, ...]:
        values = list(DEFAULT_STATUS_VALUES)
        if not DB_PATH.exists():
            return tuple(values)

        try:
            ensure_database_schema()
            with sqlite3.connect(db_uri(), uri=True) as connection:
                rows = connection.execute(
                    """
                    SELECT code
                    FROM job_statuses
                    ORDER BY sort_order, code COLLATE NOCASE
                    """
                ).fetchall()
        except sqlite3.Error:
            return tuple(values)

        db_values = []
        for row in rows:
            status = clean(row[0])
            if status and status not in db_values:
                db_values.append(status)
        if not db_values:
            return tuple(values)
        for status in values:
            if status not in db_values:
                db_values.append(status)
        return tuple(db_values)

    def _load_settings(self) -> dict[str, Any]:
        try:
            data = json.loads(SETTINGS_PATH.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            return {}
        if isinstance(data, dict):
            return data
        return {}

    def _saved_window_size(
        self,
        name: str,
        default: str,
        minimum_width: int,
        minimum_height: int,
    ) -> str:
        sizes = self.settings.get("window_sizes")
        value = sizes.get(name) if isinstance(sizes, dict) else None
        match = re.fullmatch(r"(\d+)x(\d+)", clean(value))
        if match is None:
            return default
        width = max(minimum_width, int(match.group(1)))
        height = max(minimum_height, int(match.group(2)))
        return f"{width}x{height}"

    def _remember_window_size(self, name: str, window: tk.Misc) -> None:
        if clean(window.state()) != "normal":
            return
        width = int(window.winfo_width())
        height = int(window.winfo_height())
        if width <= 1 or height <= 1:
            return

        sizes = self.settings.get("window_sizes")
        if not isinstance(sizes, dict):
            sizes = {}
            self.settings["window_sizes"] = sizes
        value = f"{width}x{height}"
        if sizes.get(name) == value:
            return
        sizes[name] = value
        self._save_settings()

    def _schedule_main_window_size_save(self, event: tk.Event[tk.Misc]) -> None:
        if event.widget is not self or clean(self.state()) != "normal":
            return
        if self._main_size_save_after_id is not None:
            self.after_cancel(self._main_size_save_after_id)
        self._main_size_save_after_id = self.after(
            300,
            self._save_main_window_size,
        )

    def _save_main_window_size(self) -> None:
        self._main_size_save_after_id = None
        self._remember_window_size("main", self)

    def _cancel_main_window_size_save(self, event: tk.Event[tk.Misc]) -> None:
        if event.widget is not self or self._main_size_save_after_id is None:
            return
        self.after_cancel(self._main_size_save_after_id)
        self._main_size_save_after_id = None

    def _close_app(self) -> None:
        if self._main_size_save_after_id is not None:
            self.after_cancel(self._main_size_save_after_id)
            self._main_size_save_after_id = None
        self._remember_window_size("main", self)
        self.destroy()

    def _saved_status_filter_value(self, status: str) -> bool:
        status_filters = self.settings.get("status_filters")
        if isinstance(status_filters, dict) and status in status_filters:
            return bool(status_filters[status])
        return True

    def _saved_job_sort_column(self) -> str | None:
        job_sort = self.settings.get("job_sort")
        column = job_sort.get("column") if isinstance(job_sort, dict) else None
        valid_columns = {name for name, _label, _width, _anchor in JOB_COLUMNS}
        if isinstance(column, str) and column in valid_columns:
            return column
        return None

    def _saved_job_sort_descending(self) -> bool:
        job_sort = self.settings.get("job_sort")
        if isinstance(job_sort, dict):
            return bool(job_sort.get("descending", False))
        return False

    def _saved_company_sort_column(self) -> str:
        company_sort = self.settings.get("company_sort")
        column = company_sort.get("column") if isinstance(company_sort, dict) else None
        if column in {
            "priority",
            "name",
            "linkedin_id",
            "application_count",
            "blacklisted",
        }:
            return clean(column)
        return "name"

    def _saved_company_sort_descending(self) -> bool:
        company_sort = self.settings.get("company_sort")
        if isinstance(company_sort, dict):
            return bool(company_sort.get("descending", False))
        return False

    def _saved_application_sort_column(self) -> str | None:
        application_sort = self.settings.get("application_sort")
        column = (
            application_sort.get("column")
            if isinstance(application_sort, dict)
            else None
        )
        if column in {"title", "company", "applied_at", "status"}:
            return clean(column)
        return None

    def _saved_application_sort_descending(self) -> bool:
        application_sort = self.settings.get("application_sort")
        if isinstance(application_sort, dict):
            return bool(application_sort.get("descending", False))
        return False

    def _filter_changed(self) -> None:
        self._save_settings()
        self.refresh_jobs()

    def _save_settings(self) -> None:
        data = dict(self.settings)
        data.update({
            "status_filters": {
                status: bool(variable.get())
                for status, variable in self.status_filter_vars.items()
            },
            "show_zero": bool(self.show_zero_var.get()),
            "job_sort": {
                "column": self.job_sort_column,
                "descending": bool(self.job_sort_descending),
            },
            "company_sort": {
                "column": self.company_sort_column,
                "descending": bool(self.company_sort_descending),
            },
            "application_sort": {
                "column": self.application_sort_column,
                "descending": bool(self.application_sort_descending),
            },
        })
        self.settings = data
        try:
            SETTINGS_PATH.write_text(
                json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
            )
        except OSError as error:
            self.status_var.set(f"Settings save failed: {error}")

    def connect_writable(self) -> sqlite3.Connection:
        if not DB_PATH.exists():
            raise FileNotFoundError(f"Database not found: {DB_PATH}")
        ensure_database_schema()
        connection = sqlite3.connect(DB_PATH)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        return connection

    def refresh_jobs(self) -> None:
        self._close_title_selection()
        try:
            rows = self._load_jobs()
        except Exception as error:
            messagebox.showerror("Refresh failed", str(error))
            self.status_var.set("Refresh failed")
            return

        self.jobs_tree.delete(*self.jobs_tree.get_children())
        self.job_rows.clear()
        self.current_source_url = ""
        self._clear_detail()

        for index, row in enumerate(rows):
            item_id = str(index)
            values = [
                format_display_date(row[name])
                if name == "added_at"
                else format_company_display(
                    row[name],
                    row["company_application_count"],
                )
                if name == "company"
                else clean(row[name])
                for name, _label, _width, _anchor in JOB_COLUMNS
            ]
            tags = ("odd",) if index % 2 else ()
            self.jobs_tree.insert("", tk.END, iid=item_id, values=values, tags=tags)
            self.job_rows[item_id] = {key: clean(row[key]) for key in row.keys()}

        self.status_var.set(f"{len(rows)} jobs")
        self.jobs_tree.reapply_sort()
        children = self.jobs_tree.get_children()
        if children:
            self.jobs_tree.selection_set(children[0])
            self.jobs_tree.focus(children[0])
            self.jobs_tree.see(children[0])
        self._schedule_job_link_labels()

    def _refresh_from_event(self, _event: tk.Event[tk.Misc]) -> str:
        self.refresh_jobs()
        return "break"

    def _clear_search_filters(self) -> None:
        self.id_search_var.set("")
        self.added_from_var.set("")
        self.reason_filter_var.set("")
        self.refresh_jobs()

    def _open_black_titles(self) -> None:
        if not BLOCKED_TITLES_PATH.is_file():
            messagebox.showerror(
                "Black titles",
                f"File not found:\n{BLOCKED_TITLES_PATH}",
            )
            self.status_var.set("Black titles file not found")
            return
        try:
            subprocess.Popen(["notepad.exe", str(BLOCKED_TITLES_PATH)])
        except OSError as error:
            messagebox.showerror("Black titles", str(error))
            self.status_var.set("Could not open Black titles")
            return
        self.status_var.set("Opened Black titles")

    def _open_config_window(self) -> None:
        window = self.config_window
        if window is not None and window.winfo_exists():
            self._refresh_config_window()
            window.deiconify()
            window.lift()
            window.focus_force()
            return

        window = tk.Toplevel(self)
        self.config_window = window
        window.title("Config")
        window.geometry(self._saved_window_size("config", "380x260", 300, 180))
        window.minsize(300, 180)
        window.transient(self)
        window.columnconfigure(0, weight=1)
        window.rowconfigure(0, weight=1)

        size_save_after_id: str | None = None

        def save_size() -> None:
            nonlocal size_save_after_id
            size_save_after_id = None
            self._remember_window_size("config", window)

        def schedule_size_save(event: tk.Event[tk.Misc]) -> None:
            nonlocal size_save_after_id
            if event.widget is not window or clean(window.state()) != "normal":
                return
            if size_save_after_id is not None:
                window.after_cancel(size_save_after_id)
            size_save_after_id = window.after(300, save_size)

        def close_window() -> None:
            nonlocal size_save_after_id
            if size_save_after_id is not None:
                window.after_cancel(size_save_after_id)
                size_save_after_id = None
            self._remember_window_size("config", window)
            self.config_window = None
            self.config_vars.clear()
            window.destroy()

        window.bind("<Configure>", schedule_size_save)
        window.protocol("WM_DELETE_WINDOW", close_window)

        self.config_rows_frame = ttk.Frame(window, padding=14)
        self.config_rows_frame.grid(row=0, column=0, sticky="nsew")
        self.config_rows_frame.columnconfigure(0, weight=1)
        self._refresh_config_window()

    def _refresh_config_window(self) -> None:
        try:
            with self.connect() as connection:
                rows = connection.execute(
                    """
                    SELECT "key", config_name, value
                    FROM config
                    ORDER BY "key"
                    """
                ).fetchall()
        except Exception as error:
            messagebox.showerror("Config load failed", str(error))
            self.status_var.set("Config load failed")
            return

        for widget in self.config_rows_frame.winfo_children():
            widget.destroy()
        self.config_vars.clear()

        for row_index, row in enumerate(rows):
            config_key = int(row["key"])
            config_name = clean(row["config_name"])
            variable = tk.BooleanVar(value=clean(row["value"]) == "1")
            checkbox = ttk.Checkbutton(
                self.config_rows_frame,
                text=config_name,
                variable=variable,
                command=lambda key=config_key, name=config_name, var=variable: (
                    self._set_config_value(key, name, var)
                ),
            )
            checkbox.grid(row=row_index, column=0, sticky="w", pady=4)
            self.config_vars[config_key] = variable

    def _set_config_value(
        self,
        config_key: int,
        config_name: str,
        variable: tk.BooleanVar,
    ) -> None:
        enabled = bool(variable.get())
        try:
            with self.connect_writable() as connection:
                result = connection.execute(
                    """
                    UPDATE config
                    SET value = ?
                    WHERE "key" = ?
                    """,
                    ("1" if enabled else "0", config_key),
                )
                if result.rowcount != 1:
                    raise KeyError(f"Config not found: {config_name}")
        except Exception as error:
            variable.set(not enabled)
            messagebox.showerror("Config update failed", str(error))
            self.status_var.set("Config update failed")
            return

        value = "1" if enabled else "0"
        self.status_var.set(f"{config_name} = {value}")

    def _jobs_tree_yview(self, *args: Any) -> None:
        self._close_title_selection()
        self.jobs_tree.yview(*args)
        self._schedule_job_link_labels()

    def _jobs_tree_xview(self, *args: Any) -> None:
        self._close_title_selection()
        self.jobs_tree.xview(*args)
        self._schedule_job_link_labels()

    def _jobs_tree_configured(self, _event: tk.Event[tk.Misc]) -> None:
        self._close_title_selection()
        self._schedule_job_link_labels()

    def _jobs_tree_mousewheel(self, _event: tk.Event[tk.Misc]) -> None:
        self._close_title_selection()
        self.after_idle(self._schedule_job_link_labels)

    def _jobs_tree_button_press(self, event: tk.Event[tk.Misc]) -> None:
        if self.jobs_tree.identify_region(event.x, event.y) != "separator":
            return
        self.job_column_resize_active = True
        self._close_title_selection()
        self._clear_job_link_labels()

    def _jobs_tree_button_drag(self, _event: tk.Event[tk.Misc]) -> None:
        if self.job_column_resize_active:
            self._clear_job_link_labels()

    def _jobs_tree_button_release(self, _event: tk.Event[tk.Misc]) -> None:
        if not self.job_column_resize_active:
            return
        self.job_column_resize_active = False
        self._schedule_job_link_labels()

    def _start_title_selection(
        self,
        event: tk.Event[tk.Misc],
    ) -> str | None:
        if self.jobs_tree.identify_region(event.x, event.y) != "cell":
            return None
        item = self.jobs_tree.identify_row(event.y)
        column_ref = self.jobs_tree.identify_column(event.x)
        columns = tuple(self.jobs_tree["columns"])
        try:
            column = columns[int(column_ref.removeprefix("#")) - 1]
        except (ValueError, IndexError):
            return None
        if not item or column != "title":
            return None

        self._close_title_selection()
        bounds = self.jobs_tree.bbox(item, column)
        row = self.job_rows.get(item)
        if not bounds or row is None:
            return "break"

        x, y, width, height = bounds
        entry = tk.Entry(
            self.jobs_tree,
            borderwidth=1,
            relief="solid",
            background="#fff2a8",
            foreground="#000000",
            selectbackground="#2d668f",
            selectforeground="#ffffff",
            exportselection=True,
        )
        entry.insert(0, clean(row.get("title", "")))
        entry.place(x=x, y=y, width=width, height=height)
        entry.bind("<KeyPress>", self._block_title_selection_edit)
        entry.bind("<Control-a>", self._select_entry_text)
        entry.bind("<Control-A>", self._select_entry_text)
        entry.bind("<Control-c>", self._copy_widget_event)
        entry.bind("<Control-C>", self._copy_widget_event)
        entry.bind("<Control-Insert>", self._copy_widget_event)
        entry.bind("<<Copy>>", self._copy_widget_event)
        entry.bind("<Button-3>", self._show_title_selection_menu)
        entry.bind("<Escape>", self._close_title_selection_from_event)
        entry.bind("<FocusOut>", self._title_selection_focus_out)
        entry.bind("<MouseWheel>", self._scroll_jobs_from_title_selection)
        entry.bind(
            "<Shift-MouseWheel>",
            self._scroll_jobs_from_title_selection,
        )
        self.title_selection_entry = entry
        self.title_selection_item = item
        entry.focus_set()
        entry.selection_range(0, tk.END)
        entry.icursor(tk.END)
        return "break"

    def _block_title_selection_edit(
        self,
        event: tk.Event[tk.Misc],
    ) -> str | None:
        key = event.keysym.lower()
        if event.state & 0x4 and key in {"a", "c", "insert"}:
            return None
        if key in {
            "left",
            "right",
            "home",
            "end",
            "tab",
            "shift_l",
            "shift_r",
            "control_l",
            "control_r",
            "escape",
        }:
            return None
        return "break"

    def _selected_title_fragment(self, entry: tk.Entry) -> str:
        try:
            first = entry.index(tk.SEL_FIRST)
            last = entry.index(tk.SEL_LAST)
        except tk.TclError:
            return ""
        return entry.get()[first:last]

    def _show_title_selection_menu(
        self,
        event: tk.Event[tk.Misc],
    ) -> str:
        entry = event.widget
        if not isinstance(entry, tk.Entry):
            return "break"
        fragment = self._selected_title_fragment(entry)
        menu = tk.Menu(self, tearoff=False)
        menu.add_command(
            label="Copy",
            command=lambda widget=entry: self._copy_widget_selection(widget),
            state=tk.NORMAL if fragment else tk.DISABLED,
        )
        menu.add_command(
            label="To blacklist",
            command=lambda widget=entry: self._add_title_selection_to_blacklist(
                widget
            ),
            state=tk.NORMAL if fragment.strip() else tk.DISABLED,
        )
        self.title_selection_menu_open = True
        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()
            self.title_selection_menu_open = False
            self.after_idle(self._close_title_selection_if_unfocused)
        return "break"

    def _add_title_selection_to_blacklist(self, entry: tk.Entry) -> None:
        fragment = self._selected_title_fragment(entry)
        try:
            added = append_unique_blacklist_term(BLOCKED_TITLES_PATH, fragment)
        except (OSError, ValueError) as error:
            messagebox.showerror("Title blacklist", str(error))
            self.status_var.set("Could not update title blacklist")
            return

        term = " ".join(fragment.split())
        if added:
            self.status_var.set(f"Added to title blacklist: {term}")
        else:
            self.status_var.set(f"Already in title blacklist: {term}")
        self._close_title_selection()

    def _title_selection_focus_out(
        self,
        _event: tk.Event[tk.Misc],
    ) -> None:
        self.after(50, self._close_title_selection_if_unfocused)

    def _close_title_selection_if_unfocused(self) -> None:
        entry = self.title_selection_entry
        if entry is None or self.title_selection_menu_open:
            return
        if self.focus_get() is not entry:
            self._close_title_selection()

    def _close_title_selection_from_event(
        self,
        _event: tk.Event[tk.Misc] | None = None,
    ) -> str:
        self._close_title_selection()
        self.jobs_tree.focus_set()
        return "break"

    def _close_title_selection(self) -> None:
        entry = self.title_selection_entry
        self.title_selection_entry = None
        self.title_selection_item = ""
        if entry is not None and entry.winfo_exists():
            entry.destroy()

    def _scroll_jobs_from_title_selection(
        self,
        event: tk.Event[tk.Misc],
    ) -> str:
        delta = int(getattr(event, "delta", 0) or 0)
        direction = -1 if delta > 0 else 1
        horizontal = bool(event.state & 0x1)
        self._close_title_selection()
        if horizontal:
            self.jobs_tree.xview_scroll(direction, "units")
        else:
            self.jobs_tree.yview_scroll(direction, "units")
        self._schedule_job_link_labels()
        return "break"

    def _schedule_job_link_labels(self) -> None:
        if self.job_column_resize_active:
            return
        if self.job_links_after_id is not None:
            return
        self.job_links_after_id = self.after_idle(self._render_job_link_labels)

    def _clear_job_link_labels(self) -> None:
        if self.job_links_after_id is not None:
            self.after_cancel(self.job_links_after_id)
            self.job_links_after_id = None
        for label in self.job_link_labels:
            if label.winfo_exists():
                label.destroy()
        self.job_link_labels.clear()

    def _render_job_link_labels(self) -> None:
        self.job_links_after_id = None
        self._clear_job_link_labels()
        if self.job_column_resize_active:
            return

        selected = set(self.jobs_tree.selection())

        def add_link(
            item: str,
            column: str,
            text: str,
            command: Any,
            background: str,
            foreground: str,
        ) -> None:
            bounds = self.jobs_tree.bbox(item, column)
            if not bounds:
                return
            x, y, width, height = bounds
            if width <= 2 or height <= 2:
                return

            label = tk.Label(
                self.jobs_tree,
                text=text,
                anchor="w",
                background=background,
                foreground=foreground,
                font=("Segoe UI", 9, "underline"),
                padx=0,
                cursor="hand2",
            )
            link_width = min(label.winfo_reqwidth(), max(1, width - 8))
            label.place(
                x=x + 4,
                y=y + 1,
                width=link_width,
                height=height - 2,
            )
            label.bind(
                "<ButtonRelease-1>",
                lambda _event, action=command: action(),
            )
            label.bind("<MouseWheel>", self._scroll_jobs_from_link)
            label.bind("<Shift-MouseWheel>", self._scroll_jobs_from_link)
            self.job_link_labels.append(label)

        for item in self.jobs_tree.get_children(""):
            row = self.job_rows.get(item)
            if not row:
                continue

            is_selected = item in selected
            background = (
                "#4b6f8d"
                if is_selected
                else ("#f7f9fb" if self.jobs_tree.index(item) % 2 else "#ffffff")
            )
            foreground = "#d9efff" if is_selected else "#005a9c"
            status = clean(row.get("status", ""))
            application_id = clean(row.get("application_id", ""))
            if status == "Applied" and application_id:
                add_link(
                    item,
                    "status",
                    status,
                    lambda value=int(application_id): (
                        self._open_applications_window(value)
                    ),
                    background,
                    foreground,
                )

            if row.get("company") and row.get("company_id"):
                company_id = int(row["company_id"])
                add_link(
                    item,
                    "company",
                    format_company_display(
                        row["company"],
                        row.get("company_application_count", 0),
                    ),
                    lambda value=company_id: self._open_companies_window(value),
                    background,
                    foreground,
                )

            added_at = format_display_date(row.get("added_at", ""))
            if added_at:
                add_link(
                    item,
                    "added_at",
                    added_at,
                    lambda value=added_at: self._set_added_from_link(value),
                    background,
                    foreground,
                )

            reason_code = clean(row.get("candidate_fit_reason_code", "")).strip()
            if reason_code:
                add_link(
                    item,
                    "candidate_fit_reason_code",
                    reason_code,
                    lambda value=reason_code: self._add_reason_filter_from_link(value),
                    background,
                    foreground,
                )

    def _scroll_jobs_from_link(self, event: tk.Event[tk.Misc]) -> str:
        direction = -1 if int(getattr(event, "delta", 0) or 0) > 0 else 1
        if event.state & 0x1:
            self.jobs_tree.xview_scroll(direction, "units")
        else:
            self.jobs_tree.yview_scroll(direction, "units")
        self._schedule_job_link_labels()
        return "break"

    def _set_added_from_link(self, value: str) -> None:
        added_from = format_display_date(value)
        try:
            parse_display_date(added_from)
        except ValueError:
            return
        self.added_from_var.set(added_from)
        self.added_from_entry.focus_set()
        self.added_from_entry.selection_range(0, tk.END)
        self.status_var.set(f"Date >= {added_from}")

    def _add_reason_filter_from_link(self, value: str) -> None:
        reason = clean(value).strip()
        if not reason:
            return
        reasons = [
            item.strip()
            for item in self.reason_filter_var.get().split(",")
            if item.strip()
        ]
        if reason.casefold() not in {item.casefold() for item in reasons}:
            reasons.append(reason)
        self.reason_filter_var.set(", ".join(reasons))
        self.reason_filter_entry.focus_set()
        self.reason_filter_entry.selection_clear()
        self.reason_filter_entry.icursor(tk.END)
        self.status_var.set(f"Reason: {', '.join(reasons)}")

    def _open_companies_window(self, company_id: int | None = None) -> None:
        window = self.companies_window
        if window is not None and window.winfo_exists():
            self._refresh_companies(company_id)
            window.deiconify()
            window.lift()
            window.focus_force()
            return

        window = tk.Toplevel(self)
        self.companies_window = window
        window.title("Companies")
        window.geometry(self._saved_window_size("companies", "940x640", 820, 360))
        window.minsize(820, 360)
        window.transient(self)
        window.columnconfigure(0, weight=1)
        window.rowconfigure(0, weight=1)

        size_save_after_id: str | None = None

        def save_size() -> None:
            nonlocal size_save_after_id
            size_save_after_id = None
            self._remember_window_size("companies", window)

        def schedule_size_save(event: tk.Event[tk.Misc]) -> None:
            nonlocal size_save_after_id
            if event.widget is not window or clean(window.state()) != "normal":
                return
            if size_save_after_id is not None:
                window.after_cancel(size_save_after_id)
            size_save_after_id = window.after(300, save_size)

        def close_window() -> None:
            nonlocal size_save_after_id
            if size_save_after_id is not None:
                window.after_cancel(size_save_after_id)
                size_save_after_id = None
            if self.company_controls_after_id is not None:
                window.after_cancel(self.company_controls_after_id)
                self.company_controls_after_id = None
            self._remember_window_size("companies", window)
            self._finish_company_linkedin_edit(save=False)
            self._destroy_company_checkbox_widgets()
            self.company_rows_by_item.clear()
            self.companies_window = None
            window.destroy()

        window.bind("<Configure>", schedule_size_save)
        window.protocol("WM_DELETE_WINDOW", close_window)

        table = ttk.Frame(window, padding=10)
        table.grid(row=0, column=0, sticky="nsew")
        table.columnconfigure(0, weight=1)
        table.rowconfigure(0, weight=1)

        company_columns = (
            ("priority", "Priority", 90, "center"),
            ("name", "Company", 360, "w"),
            ("linkedin_id", "LinkedIn ID", 170, "w"),
            ("application_count", "Applications", 110, "center"),
            ("blacklisted", "Blacklisted", 140, "center"),
        )
        self.companies_tree = SortableTreeview(
            table,
            company_columns,
            numeric_columns={"priority", "application_count", "blacklisted"},
            sort_column=self.company_sort_column,
            sort_descending=self.company_sort_descending,
            on_sorted=self._companies_sorted,
            selectmode="browse",
        )
        self.companies_tree.grid(row=0, column=0, sticky="nsew")
        self.companies_tree.column("priority", minwidth=72, stretch=False)
        self.companies_tree.column("application_count", minwidth=90, stretch=False)
        self.companies_tree.column("blacklisted", minwidth=110, stretch=False)
        self.companies_tree.tag_configure("odd", background="#f7f9fb")

        def scroll_y(*args: Any) -> None:
            self._finish_company_linkedin_edit()
            self.companies_tree.yview(*args)
            self._schedule_company_controls()

        def scroll_x(*args: Any) -> None:
            self._finish_company_linkedin_edit()
            self.companies_tree.xview(*args)
            self._schedule_company_controls()

        vertical = ttk.Scrollbar(table, orient=tk.VERTICAL, command=scroll_y)
        vertical.grid(row=0, column=1, sticky="ns")
        horizontal = ttk.Scrollbar(table, orient=tk.HORIZONTAL, command=scroll_x)
        horizontal.grid(row=1, column=0, sticky="ew")
        self.companies_tree.configure(
            yscrollcommand=vertical.set,
            xscrollcommand=horizontal.set,
        )
        self.companies_tree.bind(
            "<<TreeviewSelect>>",
            lambda _event: self._schedule_company_controls(),
        )
        self.companies_tree.bind(
            "<Configure>",
            lambda _event: self._schedule_company_controls(),
            add="+",
        )
        self.companies_tree.bind(
            "<MouseWheel>",
            self._company_tree_mousewheel,
            add="+",
        )
        self.companies_tree.bind("<Control-c>", self._copy_tree_selection)
        self.companies_tree.bind("<Control-C>", self._copy_tree_selection)
        self.companies_tree.bind("<Control-Insert>", self._copy_tree_selection)
        self.companies_tree.bind("<<Copy>>", self._copy_tree_selection)
        self.companies_tree.bind("<Button-3>", self._show_copy_menu)
        self.companies_tree.bind(
            "<Double-Button-1>",
            self._start_company_linkedin_edit,
        )

        add_form = ttk.Frame(table, padding=(0, 8, 0, 0))
        add_form.grid(row=2, column=0, columnspan=2, sticky="ew")
        add_form.columnconfigure(3, weight=1)
        ttk.Label(add_form, text="LinkedIn ID", style="Muted.TLabel").grid(
            row=0,
            column=0,
            sticky="w",
            padx=(0, 4),
        )
        self.new_company_linkedin_id_var = tk.StringVar()
        linkedin_id_entry = ttk.Entry(
            add_form,
            textvariable=self.new_company_linkedin_id_var,
            width=18,
        )
        linkedin_id_entry.grid(row=0, column=1, sticky="w", padx=(0, 12))
        self._bind_editable_entry(linkedin_id_entry)
        linkedin_id_entry.bind("<Return>", self._add_company_from_event)

        ttk.Label(add_form, text="Name", style="Muted.TLabel").grid(
            row=0,
            column=2,
            sticky="w",
            padx=(0, 4),
        )
        self.new_company_name_var = tk.StringVar()
        self.new_company_name_entry = ttk.Entry(
            add_form,
            textvariable=self.new_company_name_var,
            width=36,
        )
        self.new_company_name_entry.grid(row=0, column=3, sticky="ew")
        self._bind_editable_entry(self.new_company_name_entry)
        self.new_company_name_entry.bind("<Return>", self._add_company_from_event)
        ttk.Button(
            add_form,
            text="Add",
            command=self._add_company,
        ).grid(row=0, column=4, sticky="e", padx=(8, 0))

        self._refresh_companies(company_id)

    def _refresh_companies(self, focus_company_id: int | None = None) -> None:
        try:
            with self.connect() as connection:
                rows = connection.execute(
                    """
                    SELECT
                        c.id,
                        c.priority,
                        c.name,
                        c.linkedin_id,
                        c.blacklisted,
                        count(a.id) AS application_count
                    FROM companies c
                    LEFT JOIN jobs j ON j.company_id = c.id
                    LEFT JOIN applications a ON a.job_id = j.id
                    GROUP BY
                        c.id,
                        c.priority,
                        c.name,
                        c.linkedin_id,
                        c.blacklisted
                    """
                ).fetchall()
        except Exception as error:
            messagebox.showerror("Companies load failed", str(error))
            self.status_var.set("Companies load failed")
            return

        self.company_rows = [
            {
                "id": int(row["id"]),
                "priority": bool(row["priority"]),
                "name": clean(row["name"]),
                "linkedin_id": clean(row["linkedin_id"]),
                "application_count": int(row["application_count"]),
                "blacklisted": bool(row["blacklisted"]),
            }
            for row in rows
        ]
        if focus_company_id is not None:
            self.selected_company_id = int(focus_company_id)
        self._render_company_rows(focus_company_id)

    def _companies_sorted(self, column: str, descending: bool) -> None:
        self._finish_company_linkedin_edit()
        self.company_sort_column = column
        self.company_sort_descending = descending
        self._save_settings()
        self._schedule_company_controls()

    def _render_company_rows(self, focus_company_id: int | None = None) -> None:
        window = self.companies_window
        if window is None or not window.winfo_exists():
            return
        selected = self.companies_tree.selection()
        selected_id = selected[0] if selected else ""
        self.companies_tree.delete(*self.companies_tree.get_children(""))
        self.company_rows_by_item.clear()
        for row_index, row in enumerate(self.company_rows):
            company_id = int(row["id"])
            item = str(company_id)
            self.companies_tree.insert(
                "",
                tk.END,
                iid=item,
                values=(
                    str(int(bool(row["priority"]))),
                    clean(row["name"]),
                    clean(row["linkedin_id"]),
                    str(row["application_count"]),
                    str(int(bool(row["blacklisted"]))),
                ),
                tags=("odd",) if row_index % 2 else (),
            )
            self.company_rows_by_item[item] = row

        self.companies_tree.reapply_sort()
        focus_item = str(focus_company_id) if focus_company_id is not None else ""
        if focus_item in self.company_rows_by_item:
            selected_item = focus_item
        elif selected_id in self.company_rows_by_item:
            selected_item = selected_id
        else:
            children = self.companies_tree.get_children("")
            selected_item = children[0] if children else ""
        if selected_item:
            self.companies_tree.selection_set(selected_item)
            self.companies_tree.focus(selected_item)
            self.companies_tree.see(selected_item)
            self.selected_company_id = int(selected_item)
        self._schedule_company_controls()

    def _focus_company_row(self, company_id: int | None) -> None:
        item = str(company_id) if company_id is not None else ""
        if not item or item not in self.company_rows_by_item:
            return
        self.selected_company_id = int(item)
        self.companies_tree.selection_set(item)
        self.companies_tree.focus(item)
        self.companies_tree.see(item)
        self.companies_tree.focus_set()
        self._schedule_company_controls()

    def _schedule_company_controls(self) -> None:
        window = self.companies_window
        if window is None or not window.winfo_exists():
            return
        if self.company_controls_after_id is not None:
            window.after_cancel(self.company_controls_after_id)
        self.company_controls_after_id = window.after_idle(
            self._render_company_controls
        )

    def _render_company_controls(self) -> None:
        self.company_controls_after_id = None
        window = self.companies_window
        if window is None or not window.winfo_exists():
            return
        self._destroy_company_checkbox_widgets()
        selected = set(self.companies_tree.selection())
        if selected:
            self.selected_company_id = int(next(iter(selected)))

        for item in self.companies_tree.get_children(""):
            row = self.company_rows_by_item.get(item)
            if row is None:
                continue
            row_index = self.companies_tree.index(item)
            background = (
                "#4b6f8d"
                if item in selected
                else ("#f7f9fb" if row_index % 2 else "#ffffff")
            )
            for column, callback in (
                ("priority", self._company_priority_changed),
                ("blacklisted", self._company_blacklist_changed),
            ):
                bounds = self.companies_tree.bbox(item, column)
                if not bounds:
                    continue
                x, y, width, height = bounds
                variable = tk.BooleanVar(value=bool(row[column]))
                cell = tk.Frame(self.companies_tree, background=background)
                cell.place(x=x + 1, y=y + 1, width=width - 2, height=height - 2)
                checkbox = tk.Checkbutton(
                    cell,
                    variable=variable,
                    command=lambda value=int(item), var=variable, action=callback: (
                        action(value, var)
                    ),
                    background=background,
                    activebackground=background,
                    borderwidth=0,
                    highlightthickness=0,
                    padx=0,
                    pady=0,
                )
                checkbox.pack(expand=True)
                for widget in (cell, checkbox):
                    widget.bind(
                        "<MouseWheel>",
                        self._scroll_companies_from_control,
                        add="+",
                    )
                    widget.bind(
                        "<Shift-MouseWheel>",
                        self._scroll_companies_from_control,
                        add="+",
                    )
                self.company_checkbox_widgets.extend((cell, checkbox))

    def _destroy_company_checkbox_widgets(self) -> None:
        for widget in self.company_checkbox_widgets:
            if widget.winfo_exists():
                widget.destroy()
        self.company_checkbox_widgets.clear()

    def _company_tree_mousewheel(self, _event: tk.Event[tk.Misc]) -> None:
        self._finish_company_linkedin_edit()
        self.after_idle(self._schedule_company_controls)

    def _scroll_companies_from_control(self, event: tk.Event[tk.Misc]) -> str:
        self._finish_company_linkedin_edit()
        direction = -1 if int(getattr(event, "delta", 0) or 0) > 0 else 1
        if event.state & 0x1:
            self.companies_tree.xview_scroll(direction, "units")
        else:
            self.companies_tree.yview_scroll(direction, "units")
        self._schedule_company_controls()
        return "break"

    def _start_company_linkedin_edit(
        self,
        event: tk.Event[tk.Misc],
    ) -> str | None:
        if self.companies_tree.identify_region(event.x, event.y) != "cell":
            return None
        item = self.companies_tree.identify_row(event.y)
        column_ref = self.companies_tree.identify_column(event.x)
        columns = tuple(self.companies_tree["columns"])
        try:
            column = columns[int(column_ref.removeprefix("#")) - 1]
        except (ValueError, IndexError):
            return None
        if not item or column != "linkedin_id":
            return None

        self._finish_company_linkedin_edit()
        bounds = self.companies_tree.bbox(item, column)
        row = self.company_rows_by_item.get(item)
        if not bounds or row is None:
            return "break"

        x, y, width, height = bounds
        editor = ttk.Entry(self.companies_tree)
        editor.insert(0, clean(row["linkedin_id"]))
        editor.place(x=x, y=y, width=width, height=height)
        self._bind_editable_entry(editor)
        editor.bind("<Return>", self._finish_company_linkedin_edit)
        editor.bind("<KP_Enter>", self._finish_company_linkedin_edit)
        editor.bind("<Escape>", self._cancel_company_linkedin_edit)
        editor.bind("<FocusOut>", self._finish_company_linkedin_edit)
        self.company_linkedin_editor = editor
        self.company_linkedin_editor_item = item
        self.companies_tree.selection_set(item)
        self.companies_tree.focus(item)
        self.selected_company_id = int(item)
        editor.focus_set()
        editor.selection_range(0, tk.END)
        return "break"

    def _finish_company_linkedin_edit(
        self,
        _event: tk.Event[tk.Misc] | None = None,
        *,
        save: bool = True,
    ) -> str:
        editor = self.company_linkedin_editor
        item = self.company_linkedin_editor_item
        if editor is None:
            return "break"

        value = editor.get().strip()
        self.company_linkedin_editor = None
        self.company_linkedin_editor_item = ""
        if editor.winfo_exists():
            editor.destroy()
        if not save:
            return "break"

        row = self.company_rows_by_item.get(item)
        if row is None or value == clean(row["linkedin_id"]):
            return "break"

        try:
            with self.connect_writable() as connection:
                update_company_linkedin_id(connection, int(item), value)
        except sqlite3.IntegrityError as error:
            error_text = clean(error).casefold()
            message = (
                f"LinkedIn ID already exists: {value}"
                if "companies.linkedin_id" in error_text
                else str(error)
            )
            messagebox.showerror("Company update failed", message)
            self.status_var.set("Company update failed")
            self._schedule_company_controls()
            return "break"
        except Exception as error:
            messagebox.showerror("Company update failed", str(error))
            self.status_var.set("Company update failed")
            self._schedule_company_controls()
            return "break"

        row["linkedin_id"] = value
        self.companies_tree.set(item, "linkedin_id", value)
        if self.company_sort_column == "linkedin_id":
            self.companies_tree.reapply_sort()
        self._schedule_company_controls()
        self.status_var.set(f"{clean(row['name'])}: LinkedIn ID updated")
        return "break"

    def _cancel_company_linkedin_edit(
        self,
        _event: tk.Event[tk.Misc] | None = None,
    ) -> str:
        return self._finish_company_linkedin_edit(save=False)

    def _add_company_from_event(
        self,
        _event: tk.Event[tk.Misc] | None = None,
    ) -> str:
        self._add_company()
        return "break"

    def _add_company(self) -> None:
        name = self.new_company_name_var.get().strip()
        linkedin_id = self.new_company_linkedin_id_var.get().strip()
        if not name:
            messagebox.showerror("Company add failed", "Company name is required.")
            self.new_company_name_entry.focus_set()
            return

        try:
            with self.connect_writable() as connection:
                company_id = create_company(connection, name, linkedin_id)
        except sqlite3.IntegrityError as error:
            error_text = clean(error).casefold()
            if "companies.name" in error_text:
                message = f"Company already exists: {name}"
            elif "companies.linkedin_id" in error_text:
                message = f"LinkedIn ID already exists: {linkedin_id}"
            else:
                message = str(error)
            messagebox.showerror("Company add failed", message)
            self.status_var.set("Company add failed")
            return
        except Exception as error:
            messagebox.showerror("Company add failed", str(error))
            self.status_var.set("Company add failed")
            return

        self.new_company_linkedin_id_var.set("")
        self.new_company_name_var.set("")
        self._refresh_companies(company_id)
        self.new_company_name_entry.focus_set()
        self.status_var.set(f"Company added: {name}")

    def _company_blacklist_changed(
        self,
        company_id: int,
        variable: tk.BooleanVar,
    ) -> None:
        blacklisted = bool(variable.get())
        try:
            with self.connect_writable() as connection:
                result = connection.execute(
                    """
                    UPDATE companies
                    SET blacklisted = ?
                    WHERE id = ?
                    """,
                    (int(blacklisted), company_id),
                )
                if result.rowcount != 1:
                    raise KeyError(f"Company not found: {company_id}")
        except Exception as error:
            variable.set(not blacklisted)
            messagebox.showerror("Company update failed", str(error))
            self.status_var.set("Company update failed")
            return

        for row in self.company_rows:
            if int(row["id"]) == company_id:
                row["blacklisted"] = blacklisted
                company_name = clean(row["name"])
                break
        else:
            company_name = str(company_id)

        action = "blacklisted" if blacklisted else "removed from blacklist"
        self.status_var.set(f"{company_name}: {action}")
        item = str(company_id)
        if item in self.company_rows_by_item:
            self.companies_tree.set(item, "blacklisted", str(int(blacklisted)))
        if self.company_sort_column == "blacklisted":
            self.companies_tree.reapply_sort()
        self._schedule_company_controls()

    def _company_priority_changed(
        self,
        company_id: int,
        variable: tk.BooleanVar,
    ) -> None:
        priority = bool(variable.get())
        try:
            with self.connect_writable() as connection:
                update_company_priority(connection, company_id, priority)
        except Exception as error:
            variable.set(not priority)
            messagebox.showerror("Company update failed", str(error))
            self.status_var.set("Company update failed")
            return

        for row in self.company_rows:
            if int(row["id"]) == company_id:
                row["priority"] = priority
                company_name = clean(row["name"])
                break
        else:
            company_name = str(company_id)

        action = "priority enabled" if priority else "priority disabled"
        self.status_var.set(f"{company_name}: {action}")
        item = str(company_id)
        if item in self.company_rows_by_item:
            self.companies_tree.set(item, "priority", str(int(priority)))
        if self.company_sort_column == "priority":
            self.companies_tree.reapply_sort()
        self._schedule_company_controls()

    def _open_applications_window(
        self,
        focus_application_id: int | None = None,
    ) -> None:
        window = self.applications_window
        if window is not None and window.winfo_exists():
            self._refresh_applications(focus_application_id)
            window.deiconify()
            window.lift()
            window.focus_force()
            return

        window = tk.Toplevel(self)
        self.applications_window = window
        window.title("Applications")
        window.geometry(self._saved_window_size("applications", "980x620", 700, 360))
        window.minsize(700, 360)
        window.transient(self)
        window.columnconfigure(0, weight=1)
        window.rowconfigure(0, weight=1)

        size_save_after_id: str | None = None

        def save_size() -> None:
            nonlocal size_save_after_id
            size_save_after_id = None
            self._remember_window_size("applications", window)

        def schedule_size_save(event: tk.Event[tk.Misc]) -> None:
            nonlocal size_save_after_id
            if event.widget is not window or clean(window.state()) != "normal":
                return
            if size_save_after_id is not None:
                window.after_cancel(size_save_after_id)
            size_save_after_id = window.after(300, save_size)

        def close_window() -> None:
            nonlocal size_save_after_id
            if size_save_after_id is not None:
                window.after_cancel(size_save_after_id)
                size_save_after_id = None
            if self.application_links_after_id is not None:
                window.after_cancel(self.application_links_after_id)
                self.application_links_after_id = None
            self._remember_window_size("applications", window)
            self.application_status_editor = None
            self.application_rows_by_item.clear()
            self._destroy_application_link_labels()
            self.applications_window = None
            window.destroy()

        window.bind("<Configure>", schedule_size_save)
        window.protocol("WM_DELETE_WINDOW", close_window)

        table = ttk.Frame(window, padding=10)
        table.grid(row=0, column=0, sticky="nsew")
        table.columnconfigure(0, weight=1)
        table.rowconfigure(0, weight=1)

        application_columns = (
            ("title", "Title", 420, "w"),
            ("company", "Company", 280, "w"),
            ("applied_at", "Date", 110, "center"),
            ("status", "Status", 150, "center"),
        )
        self.applications_tree = SortableTreeview(
            table,
            application_columns,
            sort_column=self.application_sort_column,
            sort_descending=self.application_sort_descending,
            on_sorted=self._applications_sorted,
            selectmode="browse",
        )
        self.applications_tree.grid(row=0, column=0, sticky="nsew")
        self.applications_tree.column("applied_at", minwidth=90, stretch=False)
        self.applications_tree.column("status", minwidth=110, stretch=False)
        self.applications_tree.tag_configure("odd", background="#f7f9fb")

        def scroll_y(*args: Any) -> None:
            self.applications_tree.yview(*args)
            self._schedule_application_controls()

        def scroll_x(*args: Any) -> None:
            self.applications_tree.xview(*args)
            self._schedule_application_controls()

        vertical = ttk.Scrollbar(table, orient=tk.VERTICAL, command=scroll_y)
        vertical.grid(row=0, column=1, sticky="ns")
        horizontal = ttk.Scrollbar(table, orient=tk.HORIZONTAL, command=scroll_x)
        horizontal.grid(row=1, column=0, sticky="ew")
        self.applications_tree.configure(
            yscrollcommand=vertical.set,
            xscrollcommand=horizontal.set,
        )

        self.application_status_var = tk.StringVar()
        self.application_status_editor = ttk.Combobox(
            self.applications_tree,
            textvariable=self.application_status_var,
            state="readonly",
        )
        self.application_status_editor.bind(
            "<<ComboboxSelected>>",
            self._application_status_editor_changed,
        )
        self.application_status_editor.bind("<MouseWheel>", lambda _event: "break")

        self.applications_tree.bind(
            "<<TreeviewSelect>>",
            lambda _event: self._schedule_application_controls(),
        )
        self.applications_tree.bind(
            "<Configure>",
            lambda _event: self._schedule_application_controls(),
            add="+",
        )
        self.applications_tree.bind(
            "<MouseWheel>",
            lambda _event: self.after_idle(self._schedule_application_controls),
            add="+",
        )
        self.applications_tree.bind("<Control-c>", self._copy_tree_selection)
        self.applications_tree.bind("<Control-C>", self._copy_tree_selection)
        self.applications_tree.bind("<Control-Insert>", self._copy_tree_selection)
        self.applications_tree.bind("<<Copy>>", self._copy_tree_selection)
        self.applications_tree.bind("<Button-3>", self._show_copy_menu)

        self._refresh_applications(focus_application_id)

    def _applications_sorted(self, column: str, descending: bool) -> None:
        self.application_sort_column = column
        self.application_sort_descending = descending
        self._save_settings()
        self._schedule_application_controls()

    def _refresh_applications(
        self,
        focus_application_id: int | None = None,
    ) -> None:
        window = self.applications_window
        if window is None or not window.winfo_exists():
            return
        try:
            with self.connect() as connection:
                statuses = list_application_statuses(connection)
                rows = load_applications(connection)
        except Exception as error:
            messagebox.showerror("Applications load failed", str(error))
            self.status_var.set("Applications load failed")
            return

        self.application_status_values = statuses
        self.application_rows = [dict(row) for row in rows]
        self._render_application_rows(focus_application_id)

    def _render_application_rows(
        self,
        focus_application_id: int | None = None,
    ) -> None:
        window = self.applications_window
        if window is None or not window.winfo_exists():
            return
        selected = self.applications_tree.selection()
        selected_id = selected[0] if selected else ""
        self.applications_tree.delete(*self.applications_tree.get_children(""))
        self.application_rows_by_item.clear()
        for row_index, row in enumerate(self.application_rows):
            application_id = int(row["id"])
            item = str(application_id)
            self.applications_tree.insert(
                "",
                tk.END,
                iid=item,
                values=(
                    clean(row.get("title")),
                    clean(row.get("company")),
                    format_display_date(row.get("applied_at")),
                    clean(row.get("status")) or "Applied",
                ),
                tags=("odd",) if row_index % 2 else (),
            )
            self.application_rows_by_item[item] = row

        self.applications_tree.reapply_sort()
        children = self.applications_tree.get_children("")
        focus_item = str(focus_application_id) if focus_application_id is not None else ""
        if focus_item in self.application_rows_by_item:
            selected_item = focus_item
        elif selected_id in self.application_rows_by_item:
            selected_item = selected_id
        elif children:
            selected_item = children[0]
        else:
            selected_item = ""
        if selected_item:
            self.applications_tree.selection_set(selected_item)
            self.applications_tree.focus(selected_item)
            self.applications_tree.see(selected_item)
            if focus_item:
                self.applications_tree.focus_set()
        self._schedule_application_controls()

    def _schedule_application_controls(self) -> None:
        window = self.applications_window
        if window is None or not window.winfo_exists():
            return
        if self.application_links_after_id is not None:
            window.after_cancel(self.application_links_after_id)
        self.application_links_after_id = window.after_idle(
            self._render_application_controls
        )

    def _render_application_controls(self) -> None:
        self.application_links_after_id = None
        window = self.applications_window
        if window is None or not window.winfo_exists():
            return
        self._destroy_application_link_labels()
        selected = set(self.applications_tree.selection())

        for item in self.applications_tree.get_children(""):
            row = self.application_rows_by_item.get(item)
            if row is None:
                continue
            row_index = self.applications_tree.index(item)
            background = (
                "#4b6f8d"
                if item in selected
                else ("#f7f9fb" if row_index % 2 else "#ffffff")
            )
            foreground = "#ffffff" if item in selected else "#005a9c"
            self._add_application_link_label(
                item,
                "title",
                clean(row.get("title")),
                lambda value=clean(row.get("source_url")): (
                    self._focus_job_from_application(value)
                ),
                background,
                foreground,
            )
            company_id = row.get("company_id")
            if company_id is not None and clean(row.get("company")):
                self._add_application_link_label(
                    item,
                    "company",
                    clean(row.get("company")),
                    lambda value=int(company_id): self._open_companies_window(value),
                    background,
                    foreground,
                )

        self._position_application_status_editor()

    def _add_application_link_label(
        self,
        item: str,
        column: str,
        text: str,
        command: Any,
        background: str,
        foreground: str,
    ) -> None:
        if not text:
            return
        bounds = self.applications_tree.bbox(item, column)
        if not bounds:
            return
        x, y, width, height = bounds
        if width <= 8 or height <= 2:
            return
        label = tk.Label(
            self.applications_tree,
            text=text,
            anchor="w",
            background=background,
            foreground=foreground,
            font=("Segoe UI", 9, "underline"),
            padx=0,
            cursor="hand2",
        )
        label_width = min(label.winfo_reqwidth(), max(1, width - 10))
        label.place(x=x + 5, y=y + 1, width=label_width, height=height - 2)
        label.bind("<ButtonRelease-1>", lambda _event: command())
        label.bind("<Button-3>", self._show_global_copy_menu)
        label.bind("<MouseWheel>", self._scroll_applications_from_link)
        label.bind(
            "<Shift-MouseWheel>",
            self._scroll_applications_horizontally_from_link,
        )
        self.application_link_labels.append(label)

    def _destroy_application_link_labels(self) -> None:
        for label in self.application_link_labels:
            if label.winfo_exists():
                label.destroy()
        self.application_link_labels.clear()

    def _position_application_status_editor(self) -> None:
        editor = self.application_status_editor
        if editor is None or not editor.winfo_exists():
            return
        selected = self.applications_tree.selection()
        item = selected[0] if selected else ""
        row = self.application_rows_by_item.get(item)
        bounds = self.applications_tree.bbox(item, "status") if row else ()
        if not bounds:
            editor.place_forget()
            return
        x, y, width, height = bounds
        editor.configure(values=self.application_status_values)
        self.application_status_var.set(clean(row.get("status")) or "Applied")
        editor.place(x=x + 1, y=y + 1, width=width - 2, height=height - 2)
        editor.lift()

    def _application_status_editor_changed(
        self,
        _event: tk.Event[tk.Misc] | None = None,
    ) -> None:
        selected = self.applications_tree.selection()
        if not selected:
            return
        row = self.application_rows_by_item.get(selected[0])
        if row is None:
            return
        self._application_status_changed(int(row["id"]), self.application_status_var)

    def _application_status_changed(
        self,
        application_id: int,
        variable: tk.StringVar,
    ) -> None:
        row = next(
            (item for item in self.application_rows if int(item["id"]) == application_id),
            None,
        )
        if row is None:
            return
        previous = clean(row.get("status")) or "Applied"
        status = clean(variable.get())
        if status == previous:
            return

        try:
            with self.connect_writable() as connection:
                update_application_status(connection, application_id, status)
        except Exception as error:
            variable.set(previous)
            messagebox.showerror("Application update failed", str(error))
            self.status_var.set("Application update failed")
            return

        row["status"] = status
        item = str(application_id)
        if item in self.application_rows_by_item:
            self.applications_tree.set(item, "status", status)
        self.status_var.set(f"Application -> {status}")

    def _focus_job_from_application(self, source_url: str) -> None:
        if not source_url:
            return
        try:
            with self.connect() as connection:
                row = connection.execute(
                    "SELECT id, status FROM jobs WHERE source_url = ?",
                    (source_url,),
                ).fetchone()
        except Exception as error:
            messagebox.showerror("Vacancy navigation failed", str(error))
            return
        if row is None:
            messagebox.showerror("Vacancy navigation failed", "Vacancy not found.")
            return

        status = clean(row["status"])
        if status in self.status_filter_vars:
            self.status_filter_vars[status].set(True)
        self.show_zero_var.set(True)
        self.id_search_var.set(str(row["id"]))
        self.added_from_var.set("")
        self.reason_filter_var.set("")
        self._save_settings()
        self.refresh_jobs()

        item = next(
            (
                item_id
                for item_id, item_row in self.job_rows.items()
                if item_row.get("source_url") == source_url
            ),
            "",
        )
        if not item:
            messagebox.showerror(
                "Vacancy navigation failed",
                "Vacancy is hidden by the current filters.",
            )
            return
        self.jobs_tree.selection_set(item)
        self.jobs_tree.focus(item)
        self.jobs_tree.see(item)
        self.jobs_tree.event_generate("<<TreeviewSelect>>")
        if self.applications_window is not None:
            self.applications_window.withdraw()
        self.deiconify()
        self.lift()
        self.focus_force()

    def _scroll_applications_from_link(self, event: tk.Event[tk.Misc]) -> str:
        delta = int(getattr(event, "delta", 0) or 0)
        if delta:
            direction = -1 if delta > 0 else 1
        else:
            direction = -1 if int(getattr(event, "num", 0) or 0) == 4 else 1
        self.applications_tree.yview_scroll(direction, "units")
        self._schedule_application_controls()
        return "break"

    def _scroll_applications_horizontally_from_link(
        self,
        event: tk.Event[tk.Misc],
    ) -> str:
        direction = -1 if int(getattr(event, "delta", 0) or 0) > 0 else 1
        self.applications_tree.xview_scroll(direction, "units")
        self._schedule_application_controls()
        return "break"

    def _load_jobs(self) -> list[sqlite3.Row]:
        statuses = [
            status
            for status, variable in self.status_filter_vars.items()
            if variable.get()
        ]
        where_parts: list[str] = []
        parameters: list[str] = []

        if statuses:
            placeholders = ", ".join("?" for _status in statuses)
            where_parts.append(f"jl.status IN ({placeholders})")
            parameters.extend(statuses)
        else:
            where_parts.append("0")

        if not self.show_zero_var.get():
            where_parts.append(
                """
                CAST(jl.score AS INTEGER) <> 0
                """
            )

        id_query = self.id_search_var.get().strip()
        if id_query:
            where_parts.append(
                """
                EXISTS (
                    SELECT 1
                    FROM jobs j
                    JOIN source_jobs sj ON sj.id = j.source_job_ref
                    WHERE j.source_url = jl.source_url
                        AND (
                            CAST(j.id AS TEXT) = ?
                            OR sj.source_job_id = ?
                            OR j.source_url LIKE ?
                        )
                )
                """
            )
            parameters.extend([id_query, id_query, f"%{id_query}%"])

        added_from = self.added_from_var.get().strip()
        if added_from:
            try:
                added_from_date = parse_display_date(added_from)
            except ValueError as error:
                raise ValueError("Added date must use DD.MM.YYYY format.") from error
            where_parts.append("date(jl.added_at) >= date(?)")
            parameters.append(added_from_date.strftime(DATABASE_DATE_FORMAT))

        reasons = [
            item.strip()
            for item in self.reason_filter_var.get().split(",")
            if item.strip()
        ]
        if reasons:
            placeholders = ", ".join("?" for _reason in reasons)
            where_parts.append(
                f"coalesce(j.candidate_fit_reason_code, '') IN ({placeholders})"
            )
            parameters.extend(reasons)

        where_sql = "WHERE " + " AND ".join(where_parts)
        with self.connect() as connection:
            return list(
                connection.execute(
                    f"""
                    SELECT
                        jl.score,
                        jl.fit,
                        jl.interest,
                        jl.status,
                        jl.remote_scope,
                        jl.relocation,
                        jl.location,
                        jl.company,
                        jl.title,
                        jl.role,
                        jl.seniority,
                        jl.primary_language,
                        jl.salary,
                        jl.added_at,
                        jl.source_url,
                        j.company_id,
                        a.id AS application_id,
                        coalesce(company_apps.application_count, 0)
                            AS company_application_count,
                        coalesce(j.candidate_fit_reason_code, '')
                            AS candidate_fit_reason_code,
                        coalesce(j.candidate_fit_reason, '')
                            AS candidate_fit_reason
                    FROM job_list jl
                    JOIN jobs j ON j.source_url = jl.source_url
                    LEFT JOIN applications a ON a.job_id = j.id
                    LEFT JOIN (
                        SELECT
                            company_jobs.company_id,
                            count(company_applications.id) AS application_count
                        FROM jobs company_jobs
                        JOIN applications company_applications
                            ON company_applications.job_id = company_jobs.id
                        WHERE company_jobs.company_id IS NOT NULL
                        GROUP BY company_jobs.company_id
                    ) company_apps ON company_apps.company_id = j.company_id
                    {where_sql}
                    """,
                    parameters,
                )
            )

    def _on_job_selected(self, _event: tk.Event[tk.Misc]) -> None:
        self._close_title_selection()
        self._schedule_job_link_labels()
        item = self._detail_item_from_selection()
        if not item:
            self._clear_detail()
            return

        row = self.job_rows.get(item)
        if not row:
            return

        source_url = row.get("source_url", "")
        if not source_url:
            self._clear_detail()
            return

        try:
            detail, languages, technologies = self._load_detail(source_url)
        except Exception as error:
            messagebox.showerror("Load failed", str(error))
            return

        self._show_detail(detail, languages, technologies)

    def _load_detail(
        self,
        source_url: str,
    ) -> tuple[dict[str, str], list[str], list[sqlite3.Row]]:
        with self.connect() as connection:
            row = connection.execute(
                """
                SELECT
                    j.id,
                    sj.source_job_id,
                    j.title,
                    coalesce(c.name, '') AS company,
                    j.location,
                    j.remote_type,
                    j.remote_scope,
                    j.status,
                    j.relocation,
                    j.seniority,
                    j.role,
                    j.salary,
                    j.job_interest AS interest,
                    j.candidate_fit_percent AS fit,
                    CAST(ROUND(j.job_interest * j.candidate_fit_percent * j.candidate_fit_percent / 10000.0) AS INTEGER)
                        AS score,
                    j.source_url,
                    j.summary,
                    coalesce(text.readable_text, '') AS readable_text,
                    j.added_at
                FROM jobs j
                JOIN source_jobs sj ON sj.id = j.source_job_ref
                LEFT JOIN companies c ON c.id = j.company_id
                LEFT JOIN source_job_texts text
                    ON text.source_job_ref = j.source_job_ref
                WHERE j.source_url = ?
                """,
                (source_url,),
            ).fetchone()
            if row is None:
                raise KeyError(f"Job not found: {source_url}")

            job_id = int(row["id"])
            languages = [
                clean(language_row["language"])
                for language_row in connection.execute(
                    """
                    SELECT
                        l.name
                        || coalesce(': ' || nullif(jl.level, ''), '') AS language
                    FROM job_languages jl
                    JOIN languages l ON l.id = jl.language_id
                    JOIN jobs j ON j.id = jl.job_id
                    WHERE jl.job_id = ?
                    ORDER BY
                        CASE WHEN jl.language_id = j.primary_language_id THEN 0 ELSE 1 END,
                        jl.level_rank DESC,
                        l.name COLLATE NOCASE
                    """,
                    (job_id,),
                )
            ]

            technologies = list(
                connection.execute(
                    """
                    SELECT
                        t.name AS technology,
                        CASE jt.requirement_type
                            WHEN 'core' THEN 'core'
                            WHEN 'required' THEN 'req'
                            WHEN 'important' THEN 'imp'
                            WHEN 'desired' THEN 'des'
                            WHEN 'nice_to_have' THEN 'opt'
                            ELSE jt.requirement_type
                        END AS req,
                        CASE
                            WHEN jt.requirement_type = 'nice_to_have' THEN 'nice to have'
                            WHEN lower(coalesce(jt.level, '')) IN (
                                '',
                                'listed',
                                'mentioned',
                                'required',
                                'required/listed'
                            ) THEN
                                CASE jt.level_rank
                                    WHEN 1 THEN 'nice to have'
                                    WHEN 2 THEN 'junior'
                                    WHEN 3 THEN 'regular'
                                    WHEN 4 THEN 'advanced'
                                    WHEN 5 THEN 'master'
                                    ELSE ''
                                END
                            WHEN lower(coalesce(jt.level, '')) LIKE '%experience required%'
                                THEN 'regular'
                            ELSE jt.level
                        END AS level,
                        jt.raw_value
                    FROM job_technologies jt
                    JOIN technologies t ON t.id = jt.technology_id
                    WHERE jt.job_id = ?
                    ORDER BY
                        CASE jt.requirement_type
                            WHEN 'core' THEN 1
                            WHEN 'required' THEN 2
                            WHEN 'important' THEN 3
                            WHEN 'desired' THEN 4
                            WHEN 'nice_to_have' THEN 5
                            ELSE 9
                        END,
                        jt.level_rank DESC,
                        t.name COLLATE NOCASE
                    """,
                    (job_id,),
                )
            )

        detail = {key: clean(row[key]) for key in row.keys()}
        detail["languages"] = "; ".join(language for language in languages if language)
        return detail, languages, technologies

    def _show_detail(
        self,
        detail: dict[str, str],
        _languages: list[str],
        technologies: list[sqlite3.Row],
    ) -> None:
        self.current_source_url = detail.get("source_url", "")
        self.current_status = detail.get("status", "")
        self.current_fit = detail.get("fit", "")
        self.current_interest = detail.get("interest", "")
        self._set_text_widget(self.link_text, self.current_source_url)

        for name, _label in DETAIL_FIELDS:
            value = detail.get(name, "")
            if name == "added_at":
                value = format_display_date(value)
            self._set_text_widget(self.detail_fields[name], value)
        self._set_status_buttons_state(True)

        self.tech_table.reset_sort()
        self.tech_rows = [
            {
                name: clean(row[name])
                for name, _label, _width, _anchor in TECH_COLUMNS
            }
            for row in technologies
        ]
        self.tech_table.set_rows(self.tech_rows)

        self._set_summary(detail.get("summary", ""))
        self._set_text_widget(
            self.readable_text,
            detail.get("readable_text", ""),
        )

    def _clear_detail(self) -> None:
        self._set_text_widget(self.link_text, "")
        self.current_status = ""
        self.current_fit = ""
        self.current_interest = ""
        for field in self.detail_fields.values():
            self._set_text_widget(field, "")
        self._set_status_buttons_state(False)
        self.tech_table.reset_sort()
        self.tech_rows = []
        self.tech_table.set_rows(self.tech_rows)
        self._set_summary("")
        self._set_text_widget(self.readable_text, "")

    def _set_summary(self, value: str) -> None:
        self.summary_text.delete("1.0", tk.END)
        if value:
            self.summary_text.insert("1.0", value)
        self.summary_text.mark_set(tk.INSERT, "1.0")

    def _open_current_link(self, _event: tk.Event[tk.Misc] | None = None) -> None:
        if self.current_source_url:
            webbrowser.open_new_tab(self.current_source_url)

    def _start_linkedin_availability_check(self) -> None:
        if self.availability_check_running:
            return
        if self.refilter_running:
            self.status_var.set("Refilter is running")
            return

        closed_status = self._closed_status_value()
        if not closed_status:
            self._append_availability_log(
                "start_failed",
                error="Closed status is not available in job_statuses.",
            )
            messagebox.showerror(
                "LinkedIn check failed",
                "Closed status is not available in job_statuses.",
            )
            return

        try:
            candidates = self._load_linkedin_availability_candidates()
        except Exception as error:
            self._append_availability_log("start_failed", error=str(error))
            messagebox.showerror("LinkedIn check failed", str(error))
            self.status_var.set("LinkedIn check failed")
            return

        if not candidates:
            self._append_availability_log("no_candidates")
            self.status_var.set("No New LinkedIn jobs with score > 0")
            return

        self._append_availability_log(
            "start",
            total=len(candidates),
            closed_status=closed_status,
            delay_seconds=LINKEDIN_CHECK_DELAY_SECONDS,
        )
        self.availability_check_running = True
        self.availability_queue = queue.SimpleQueue()
        if self.availability_button is not None:
            self.availability_button.configure(state=tk.DISABLED)
        self._set_refilter_buttons_state(False)
        self.status_var.set(f"LinkedIn check 0/{len(candidates)}")

        thread = threading.Thread(
            target=self._linkedin_availability_worker,
            args=(candidates, closed_status),
            daemon=True,
        )
        thread.start()
        self.after(200, self._poll_linkedin_availability_queue)

    def _start_refilter_database(self) -> None:
        self._start_refilter_collect(preview=False)

    def _start_refilter_detail(self) -> None:
        self._start_refilter_collect(preview=True)

    def _start_refilter_collect(self, *, preview: bool) -> None:
        if self.refilter_running:
            return
        if self.availability_check_running:
            self.status_var.set("LinkedIn check is running")
            return

        self._begin_refilter_activity(
            "Refilter detail running" if preview else "Refilter running"
        )

        thread = threading.Thread(
            target=self._refilter_collect_worker,
            args=(preview,),
            daemon=True,
        )
        thread.start()
        self.after(200, self._poll_refilter_queue)

    def _begin_refilter_activity(self, status: str) -> None:
        self.refilter_running = True
        self.refilter_queue = queue.SimpleQueue()
        self._set_refilter_buttons_state(False)
        if self.availability_button is not None:
            self.availability_button.configure(state=tk.DISABLED)
        self.status_var.set(status)

    def _set_refilter_buttons_state(self, enabled: bool) -> None:
        state = tk.NORMAL if enabled else tk.DISABLED
        for button in (self.refilter_button, self.refilter_detail_button):
            if button is not None:
                button.configure(state=state)

    def _refilter_collect_worker(self, preview: bool) -> None:
        try:
            candidates = collect_rejected_jobs(DB_PATH)
            if preview:
                self.refilter_queue.put(("preview", candidates))
                return

            removed_total = self._delete_refilter_candidates(candidates)
            self.refilter_queue.put(("deleted", removed_total))
        except Exception as error:
            self.refilter_queue.put(("error", str(error)))

    def _delete_refilter_candidates(
        self,
        candidates: list[dict[str, Any]],
    ) -> int:
        connection = sqlite3.connect(DB_PATH)
        try:
            connection.execute("PRAGMA foreign_keys = ON")
            deleted_ids = delete_jobs(
                connection,
                [int(candidate["id"]) for candidate in candidates],
            )
            connection.commit()
        except Exception:
            connection.rollback()
            raise
        finally:
            connection.close()
        return len(deleted_ids)

    def _start_confirmed_refilter_delete(
        self,
        candidates: list[dict[str, Any]],
    ) -> None:
        if self.refilter_running:
            return
        if self.availability_check_running:
            self.status_var.set("LinkedIn check is running")
            return

        self._begin_refilter_activity("Refilter deleting confirmed jobs")
        thread = threading.Thread(
            target=self._refilter_delete_worker,
            args=(candidates,),
            daemon=True,
        )
        thread.start()
        self.after(200, self._poll_refilter_queue)

    def _refilter_delete_worker(self, candidates: list[dict[str, Any]]) -> None:
        try:
            removed_total = self._delete_refilter_candidates(candidates)
            self.refilter_queue.put(("deleted", removed_total))
        except Exception as error:
            self.refilter_queue.put(("error", str(error)))

    def _poll_refilter_queue(self) -> None:
        while True:
            try:
                message = self.refilter_queue.get_nowait()
            except queue.Empty:
                break

            kind = message[0]
            if kind == "deleted":
                _kind, removed_total = message
                self._finish_refilter_activity()
                self.refresh_jobs()
                self.status_var.set(f"Refilter removed {removed_total} jobs")
            elif kind == "preview":
                _kind, candidates = message
                self._finish_refilter_activity()
                self.status_var.set(
                    f"Refilter detail found {len(candidates)} jobs"
                )
                self._show_refilter_detail(candidates)
            elif kind == "error":
                _kind, error_message = message
                self._finish_refilter_activity()
                messagebox.showerror("Refilter failed", error_message)
                self.status_var.set("Refilter failed")

        if self.refilter_running:
            self.after(200, self._poll_refilter_queue)

    def _finish_refilter_activity(self) -> None:
        self.refilter_running = False
        self._set_refilter_buttons_state(True)
        if self.availability_button is not None and not self.availability_check_running:
            self.availability_button.configure(state=tk.NORMAL)

    def _show_refilter_detail(self, candidates: list[dict[str, Any]]) -> None:
        dialog = tk.Toplevel(self)
        dialog.title("Refilter detail")
        dialog.geometry(
            self._saved_window_size("refilter_detail", "1100x500", 800, 300)
        )
        dialog.minsize(800, 300)
        dialog.transient(self)
        dialog.columnconfigure(0, weight=1)
        dialog.rowconfigure(1, weight=1)

        detail_size_save_after_id: str | None = None

        def save_detail_size() -> None:
            nonlocal detail_size_save_after_id
            detail_size_save_after_id = None
            self._remember_window_size("refilter_detail", dialog)

        def schedule_detail_size_save(event: tk.Event[tk.Misc]) -> None:
            nonlocal detail_size_save_after_id
            if event.widget is not dialog or clean(dialog.state()) != "normal":
                return
            if detail_size_save_after_id is not None:
                dialog.after_cancel(detail_size_save_after_id)
            detail_size_save_after_id = dialog.after(300, save_detail_size)

        def flush_detail_size() -> None:
            nonlocal detail_size_save_after_id
            if detail_size_save_after_id is not None:
                dialog.after_cancel(detail_size_save_after_id)
                detail_size_save_after_id = None
            self._remember_window_size("refilter_detail", dialog)

        def cancel_pending_detail_size_save(event: tk.Event[tk.Misc]) -> None:
            nonlocal detail_size_save_after_id
            if event.widget is not dialog or detail_size_save_after_id is None:
                return
            dialog.after_cancel(detail_size_save_after_id)
            detail_size_save_after_id = None

        dialog.bind("<Configure>", schedule_detail_size_save)
        dialog.bind("<Destroy>", cancel_pending_detail_size_save, add="+")

        ttk.Label(
            dialog,
            text=f"Rejected jobs: {len(candidates)}",
            style="Title.TLabel",
            padding=(10, 10, 10, 6),
        ).grid(row=0, column=0, sticky="w")

        table_frame = ttk.Frame(dialog, padding=(10, 0))
        table_frame.grid(row=1, column=0, sticky="nsew")
        table_frame.columnconfigure(0, weight=1)
        table_frame.rowconfigure(0, weight=1)

        canvas = tk.Canvas(
            table_frame,
            borderwidth=0,
            highlightthickness=0,
            background="#ffffff",
        )
        canvas.grid(row=0, column=0, sticky="nsew")

        y_scroll = ttk.Scrollbar(
            table_frame,
            orient=tk.VERTICAL,
            command=canvas.yview,
        )
        y_scroll.grid(row=0, column=1, sticky="ns")
        x_scroll = ttk.Scrollbar(
            table_frame,
            orient=tk.HORIZONTAL,
            command=canvas.xview,
        )
        x_scroll.grid(row=1, column=0, sticky="ew")
        canvas.configure(
            yscrollcommand=y_scroll.set,
            xscrollcommand=x_scroll.set,
        )

        rows_frame = ttk.Frame(canvas)
        rows_window = canvas.create_window((0, 0), window=rows_frame, anchor="nw")

        def update_scroll_region(_event: tk.Event[tk.Misc]) -> None:
            canvas.configure(scrollregion=canvas.bbox("all"))

        def fit_rows_to_canvas(event: tk.Event[tk.Misc]) -> None:
            canvas.itemconfigure(
                rows_window,
                width=max(event.width, rows_frame.winfo_reqwidth()),
            )

        def wheel_direction(event: tk.Event[tk.Misc]) -> int:
            delta = int(getattr(event, "delta", 0) or 0)
            if delta:
                return -1 if delta > 0 else 1
            return -1 if int(getattr(event, "num", 0) or 0) == 4 else 1

        def scroll_rows(event: tk.Event[tk.Misc]) -> str:
            canvas.yview_scroll(wheel_direction(event), "units")
            return "break"

        def scroll_columns(event: tk.Event[tk.Misc]) -> str:
            canvas.xview_scroll(wheel_direction(event), "units")
            return "break"

        def bind_canvas_wheel(widget: tk.Misc) -> None:
            widget.bind("<MouseWheel>", scroll_rows, add="+")
            widget.bind("<Shift-MouseWheel>", scroll_columns, add="+")
            widget.bind("<Button-4>", scroll_rows, add="+")
            widget.bind("<Button-5>", scroll_rows, add="+")

        rows_frame.bind("<Configure>", update_scroll_region)
        canvas.bind("<Configure>", fit_rows_to_canvas)
        bind_canvas_wheel(canvas)
        bind_canvas_wheel(rows_frame)

        def one_line(value: Any) -> str:
            return " ".join(clean(value).split())

        def wrapped_line_count(value: str, width: int) -> int:
            lines = textwrap.wrap(
                value,
                width=max(1, width - 2),
                break_long_words=True,
                break_on_hyphens=False,
            )
            return max(1, len(lines))

        def bind_source_link(cell: tk.Text, source_url: str) -> None:
            press_position: dict[str, int] = {}

            def remember_press(event: tk.Event[tk.Misc]) -> None:
                press_position["x"] = event.x
                press_position["y"] = event.y

            def open_without_drag(event: tk.Event[tk.Misc]) -> str | None:
                if not source_url:
                    return None
                distance = abs(event.x - press_position.get("x", event.x)) + abs(
                    event.y - press_position.get("y", event.y)
                )
                if distance > 4:
                    return None
                webbrowser.open_new_tab(source_url)
                return "break"

            cell.configure(cursor="hand2")
            cell.bind("<ButtonPress-1>", remember_press, add="+")
            cell.bind("<ButtonRelease-1>", open_without_drag, add="+")

        columns = (
            ("id", "ID", 9, "center"),
            ("title", "Title", 30, "left"),
            ("fit", "Fit", 7, "center"),
            ("original", "Original", 74, "left"),
            ("matched", "Match", 24, "left"),
        )
        headers: dict[str, tk.Label] = {}
        for column_index, (name, label, width, justify) in enumerate(columns):
            header = tk.Label(
                rows_frame,
                text=label,
                width=width,
                anchor="w" if justify == "left" else justify,
                background="#e7e9e7",
                foreground="#202020",
                font=("Segoe UI", 9, "bold"),
                borderwidth=1,
                relief="solid",
                padx=4,
                pady=3,
            )
            header.grid(row=0, column=column_index, sticky="nsew")
            bind_canvas_wheel(header)
            headers[name] = header

        row_cells: list[CopyableText] = []

        def render_candidates(displayed: list[dict[str, Any]]) -> None:
            for cell in row_cells:
                cell.destroy()
            row_cells.clear()

            for index, candidate in enumerate(displayed):
                background = "#f7f9fb" if index % 2 else "#ffffff"
                values = tuple(
                    one_line(value)
                    for value in (
                        candidate.get("id", ""),
                        candidate.get("title", ""),
                        candidate.get("fit", ""),
                        candidate.get("original", ""),
                        candidate.get("matched", ""),
                    )
                )
                row_height = max(
                    wrapped_line_count(value, width)
                    for (_name, _label, width, _justify), value in zip(columns, values)
                )
                for column_index, (
                    (_name, _label, width, justify),
                    value,
                ) in enumerate(
                    zip(columns, values)
                ):
                    cell = CopyableText(
                        rows_frame,
                        readonly=True,
                        copy_callback=lambda: self.status_var.set("Copied"),
                        width=width,
                        height=row_height,
                        wrap="word",
                        borderwidth=0,
                        relief="flat",
                        background=background,
                        foreground="#303030",
                        font=("Segoe UI", 9),
                        padx=4,
                        pady=3,
                    )
                    cell.tag_configure("value", justify=justify)
                    cell.insert("1.0", value, "value")
                    cell.grid(
                        row=index + 1,
                        column=column_index,
                        sticky="nsew",
                        padx=1,
                        pady=1,
                    )
                    bind_canvas_wheel(cell)
                    if column_index == 1:
                        cell.configure(foreground="#005a9c")
                        bind_source_link(
                            cell,
                            clean(candidate.get("source_url", "")),
                        )
                    row_cells.append(cell)

        sorter = SortableRows(
            tuple(
                (name, label, width, justify)
                for name, label, width, justify in columns
            ),
            {"id", "fit"},
        )

        def sort_candidates(column: str) -> None:
            sorter.sort_by(column)
            displayed = sorter.sorted_rows(candidates)
            for name, label, _width, _justify in columns:
                headers[name].configure(text=sorter._sort_heading_text(name, label))
            render_candidates(displayed)
            dialog.after_idle(lambda: canvas.yview_moveto(0))

        for name, header in headers.items():
            header.configure(cursor="hand2")
            header.bind(
                "<ButtonRelease-1>",
                lambda _event, column=name: sort_candidates(column),
            )
        render_candidates(candidates)

        actions = ttk.Frame(dialog, padding=10)
        actions.grid(row=2, column=0, sticky="ew")
        actions.columnconfigure(0, weight=1)

        def cancel() -> None:
            flush_detail_size()
            dialog.destroy()
            self.status_var.set("Refilter detail cancelled")

        def confirm() -> None:
            flush_detail_size()
            dialog.destroy()
            self._start_confirmed_refilter_delete(candidates)

        ttk.Button(actions, text="Cancel", command=cancel).grid(
            row=0,
            column=1,
            sticky="e",
            padx=(0, 6),
        )
        confirm_button = ttk.Button(actions, text="Confirm", command=confirm)
        confirm_button.grid(row=0, column=2, sticky="e")
        if not candidates:
            confirm_button.configure(state=tk.DISABLED)

        dialog.protocol("WM_DELETE_WINDOW", cancel)
        dialog.grab_set()
        dialog.lift()
        dialog.after_idle(lambda: (canvas.xview_moveto(0), canvas.yview_moveto(0)))

    def _closed_status_value(self) -> str:
        for status in self.status_values:
            if status.lower() == "closed":
                return status
        return ""

    def _load_linkedin_availability_candidates(self) -> list[dict[str, str]]:
        score_sql = """
            CAST(ROUND(
                j.job_interest * j.candidate_fit_percent * j.candidate_fit_percent
                / 10000.0
            ) AS INTEGER)
        """
        with self.connect() as connection:
            rows = connection.execute(
                f"""
                SELECT
                    j.source_url,
                    j.title,
                    coalesce(c.name, '') AS company,
                    {score_sql} AS score
                FROM jobs j
                LEFT JOIN companies c ON c.id = j.company_id
                WHERE j.status = ?
                    AND {score_sql} > 0
                    AND j.source_url LIKE ?
                ORDER BY
                    score DESC,
                    j.added_at DESC,
                    j.id DESC
                """,
                ("New", "%linkedin.com/%"),
            ).fetchall()
        return [{key: clean(row[key]) for key in row.keys()} for row in rows]

    def _append_availability_log(self, event: str, **fields: Any) -> None:
        record = {
            "ts": datetime.now().isoformat(timespec="seconds"),
            "event": event,
            **fields,
        }
        try:
            with self.availability_log_lock:
                with AVAILABILITY_LOG_PATH.open("a", encoding="utf-8") as log_file:
                    log_file.write(
                        json.dumps(record, ensure_ascii=False, sort_keys=True) + "\n"
                    )
        except OSError:
            pass

    def _linkedin_availability_worker(
        self,
        candidates: list[dict[str, str]],
        closed_status: str,
    ) -> None:
        total = len(candidates)
        processed = 0
        requests_sent = 0
        closed_count = 0
        skipped = 0
        error_message = ""

        try:
            for candidate in candidates:
                source_url = candidate.get("source_url", "")
                job_id = self._linkedin_job_id(source_url)
                if not job_id:
                    processed += 1
                    skipped += 1
                    self._append_availability_log(
                        "skip",
                        reason="no_linkedin_id",
                        source_url=source_url,
                        title=candidate.get("title", ""),
                        company=candidate.get("company", ""),
                        score=candidate.get("score", ""),
                    )
                    self.availability_queue.put(
                        ("progress", processed, total, closed_count, skipped, "")
                    )
                    continue

                if requests_sent > 0:
                    time.sleep(LINKEDIN_CHECK_DELAY_SECONDS)

                requests_sent += 1
                self._append_availability_log(
                    "request",
                    processed=processed,
                    total=total,
                    job_id=job_id,
                    source_url=source_url,
                    title=candidate.get("title", ""),
                    company=candidate.get("company", ""),
                    score=candidate.get("score", ""),
                )
                self.availability_queue.put(
                    (
                        "progress",
                        processed,
                        total,
                        closed_count,
                        skipped,
                        f"checking {job_id}",
                    )
                )
                state, message = self._fetch_linkedin_availability(job_id)
                processed += 1
                self._append_availability_log(
                    "response",
                    processed=processed,
                    total=total,
                    job_id=job_id,
                    source_url=source_url,
                    result=state,
                    message=message,
                )

                if state == "closed":
                    updated_count = self._mark_jobs_closed(
                        [source_url],
                        closed_status,
                    )
                    closed_count += updated_count
                    self._append_availability_log(
                        "closed",
                        job_id=job_id,
                        source_url=source_url,
                        updated=updated_count,
                    )
                elif state == "not_found":
                    skipped += 1
                    self._append_availability_log(
                        "skip",
                        reason="http_404",
                        processed=processed,
                        total=total,
                        job_id=job_id,
                        source_url=source_url,
                        error=message,
                    )
                elif state != "available":
                    error_message = f"{job_id}: {message}"
                    self._append_availability_log(
                        "stop_error",
                        processed=processed,
                        total=total,
                        job_id=job_id,
                        source_url=source_url,
                        error=message,
                    )
                    break

                self.availability_queue.put(
                    ("progress", processed, total, closed_count, skipped, "")
                )
        except Exception as error:
            error_message = str(error)
            self._append_availability_log("worker_exception", error=error_message)

        self._append_availability_log(
            "done",
            processed=processed,
            total=total,
            closed=closed_count,
            skipped=skipped,
            error=error_message,
        )
        self.availability_queue.put(
            ("done", processed, total, closed_count, skipped, error_message)
        )

    def _linkedin_job_id(self, source_url: str) -> str:
        match = LINKEDIN_JOB_ID_PATTERN.search(source_url)
        if match:
            return match.group(1)
        return ""

    def _fetch_linkedin_availability(self, job_id: str) -> tuple[str, str]:
        endpoint = f"https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/{job_id}"
        request = urllib.request.Request(
            endpoint,
            headers={
                "User-Agent": (
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/126.0 Safari/537.36"
                ),
                "Accept": "text/html,application/xhtml+xml",
            },
        )

        try:
            with urllib.request.urlopen(
                request,
                timeout=LINKEDIN_CHECK_TIMEOUT_SECONDS,
            ) as response:
                status_code = response.getcode()
                content_type = response.headers.get("Content-Type", "")
                body = response.read(1_000_000)
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return "not_found", "LinkedIn returned HTTP 404."
            if error.code == 429:
                return "error", "LinkedIn returned 429; stopped to avoid rate limit."
            return "error", f"LinkedIn returned HTTP {error.code}."
        except urllib.error.URLError as error:
            return "error", f"Network error: {error.reason}"
        except TimeoutError:
            return "error", "Request timed out."

        if status_code == 404:
            return "not_found", "LinkedIn returned HTTP 404."
        if status_code == 429:
            return "error", "LinkedIn returned 429; stopped to avoid rate limit."
        if status_code != 200:
            return "error", f"LinkedIn returned HTTP {status_code}."
        if content_type and "html" not in content_type.lower():
            return "error", f"Unexpected content type: {content_type}"

        html = body.decode("utf-8", errors="replace")
        html_lower = html.lower()
        if LINKEDIN_CLOSED_MARKER in html_lower:
            return "closed", ""
        if len(html) < 1000:
            return "error", "Unexpected short HTML."
        if "linkedin" not in html_lower or "job" not in html_lower:
            return "error", "Unexpected HTML from LinkedIn."
        return "available", ""

    def _mark_jobs_closed(self, source_urls: list[str], closed_status: str) -> int:
        if not source_urls:
            return 0

        with self.connect_writable() as connection:
            placeholders = ", ".join("?" for _source_url in source_urls)
            result = connection.execute(
                f"""
                UPDATE jobs
                SET status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE status = ?
                    AND source_url IN ({placeholders})
                """,
                [closed_status, "New", *source_urls],
            )
            return result.rowcount

    def _poll_linkedin_availability_queue(self) -> None:
        while True:
            try:
                message = self.availability_queue.get_nowait()
            except queue.Empty:
                break

            kind = message[0]
            if kind == "progress":
                _kind, processed, total, closed_count, skipped, detail = message
                self._show_linkedin_availability_progress(
                    processed,
                    total,
                    closed_count,
                    skipped,
                    detail,
                )
            elif kind == "done":
                _kind, processed, total, closed_count, skipped, error_message = message
                self._finish_linkedin_availability_check(
                    processed,
                    total,
                    closed_count,
                    skipped,
                    error_message,
                )

        if self.availability_check_running:
            self.after(200, self._poll_linkedin_availability_queue)

    def _show_linkedin_availability_progress(
        self,
        processed: int,
        total: int,
        closed_count: int,
        skipped: int,
        detail: str,
    ) -> None:
        suffix = f" - {detail}" if detail else ""
        self.status_var.set(
            f"LinkedIn check {processed}/{total}; "
            f"closed {closed_count}; skipped {skipped}{suffix}"
        )

    def _finish_linkedin_availability_check(
        self,
        processed: int,
        total: int,
        closed_count: int,
        skipped: int,
        error_message: str,
    ) -> None:
        self.availability_check_running = False
        if self.availability_button is not None:
            self.availability_button.configure(state=tk.NORMAL)
        if not self.refilter_running:
            self._set_refilter_buttons_state(True)

        self.refresh_jobs()
        summary = (
            f"LinkedIn check {processed}/{total}; "
            f"closed {closed_count}; skipped {skipped}"
        )
        if error_message:
            self.status_var.set(f"{summary}; stopped")
            self._append_availability_log(
                "popup_error",
                processed=processed,
                total=total,
                closed=closed_count,
                skipped=skipped,
                error=error_message,
            )
            messagebox.showerror("LinkedIn check stopped", error_message)
            return

        self.status_var.set(summary)

    def _set_current_status(self, status: str) -> None:
        items = self._selected_job_items()
        source_urls = [
            self.job_rows[item]["source_url"]
            for item in items
            if self.job_rows.get(item, {}).get("source_url")
        ]
        if not source_urls and self.current_source_url:
            source_urls = [self.current_source_url]

        if not source_urls:
            return

        created_applications = 0
        application_ids: dict[str, str] = {}
        company_application_counts: dict[str, str] = {}
        try:
            with self.connect_writable() as connection:
                placeholders = ", ".join("?" for _source_url in source_urls)
                result = connection.execute(
                    f"""
                    UPDATE jobs
                    SET status = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE source_url IN ({placeholders})
                    """,
                    [status, *source_urls],
                )
                if result.rowcount == 0:
                    raise KeyError("Selected jobs were not found.")
                if status == "Applied":
                    created_applications = create_applications_for_source_urls(
                        connection,
                        source_urls,
                        datetime.now().strftime(DATABASE_DATE_FORMAT),
                    )
                    application_ids = {
                        clean(row["source_url"]): str(row["application_id"])
                        for row in connection.execute(
                            f"""
                            SELECT j.source_url, a.id AS application_id
                            FROM jobs j
                            JOIN applications a ON a.job_id = j.id
                            WHERE j.source_url IN ({placeholders})
                            """,
                            source_urls,
                        )
                    }
                    company_application_counts = {
                        str(row["company_id"]): str(row["application_count"])
                        for row in connection.execute(
                            f"""
                            SELECT
                                company_jobs.company_id,
                                count(company_applications.id)
                                    AS application_count
                            FROM jobs company_jobs
                            JOIN applications company_applications
                                ON company_applications.job_id = company_jobs.id
                            WHERE company_jobs.company_id IN (
                                SELECT company_id
                                FROM jobs
                                WHERE source_url IN ({placeholders})
                                    AND company_id IS NOT NULL
                            )
                            GROUP BY company_jobs.company_id
                            """,
                            source_urls,
                        )
                    }
        except Exception as error:
            messagebox.showerror("Status update failed", str(error))
            self.status_var.set("Status update failed")
            return

        self._update_selected_jobs_status(status, items, source_urls)
        if application_ids:
            for row in self.job_rows.values():
                source_url = row.get("source_url", "")
                if source_url in application_ids:
                    row["application_id"] = application_ids[source_url]
            self._schedule_job_link_labels()
        if company_application_counts:
            self._update_job_company_application_counts(
                company_application_counts
            )
        if created_applications:
            if self.companies_window is not None and self.companies_window.winfo_exists():
                self._refresh_companies(self.selected_company_id)
            if (
                self.applications_window is not None
                and self.applications_window.winfo_exists()
            ):
                self._refresh_applications()
        self.status_var.set(f"Status -> {status} ({len(source_urls)})")

    def _save_scores_from_detail(self, _event: tk.Event[tk.Misc] | None = None) -> str:
        if not self.current_source_url:
            return "break"

        fit_text = self._widget_text(self.detail_fields["fit"])
        interest_text = self._widget_text(self.detail_fields["interest"])
        if fit_text == self.current_fit and interest_text == self.current_interest:
            return "break"

        try:
            fit = int(fit_text)
            interest = int(interest_text)
        except ValueError:
            messagebox.showerror("Invalid scores", "Fit and Interest must be integers.")
            self._restore_score_fields()
            return "break"

        if fit < 0 or fit > 100:
            messagebox.showerror("Invalid fit", "Fit must be between 0 and 100.")
            self._restore_score_fields()
            return "break"
        if interest < 0:
            messagebox.showerror("Invalid interest", "Interest must be 0 or greater.")
            self._restore_score_fields()
            return "break"

        score = calculate_score(fit, interest)
        try:
            with self.connect_writable() as connection:
                result = connection.execute(
                    """
                    UPDATE jobs
                    SET candidate_fit_percent = ?,
                        job_interest = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE source_url = ?
                    """,
                    (fit, interest, self.current_source_url),
                )
                if result.rowcount != 1:
                    raise KeyError(f"Job not found: {self.current_source_url}")
        except Exception as error:
            messagebox.showerror("Score update failed", str(error))
            self._restore_score_fields()
            self.status_var.set("Score update failed")
            return "break"

        self.current_fit = str(fit)
        self.current_interest = str(interest)
        self._set_text_widget(self.detail_fields["fit"], self.current_fit)
        self._set_text_widget(self.detail_fields["interest"], self.current_interest)
        self._set_text_widget(self.detail_fields["score"], str(score))
        self._update_selected_job_scores(score, fit, interest)
        self.status_var.set(f"Scores -> {score}")
        return "break"

    def _restore_score_fields(self) -> None:
        self._set_text_widget(self.detail_fields["fit"], self.current_fit)
        self._set_text_widget(self.detail_fields["interest"], self.current_interest)

    def _update_selected_jobs_status(
        self,
        status: str,
        items: list[str],
        source_urls: list[str],
    ) -> None:
        source_url_set = set(source_urls)
        if not items:
            items = [
                item
                for item, row in self.job_rows.items()
                if row.get("source_url") in source_url_set
            ]

        if not self._status_visible(status):
            for item in items:
                self.jobs_tree.delete(item)
                self.job_rows.pop(item, None)
            self._clear_detail()
            return

        columns = [name for name, _label, _width, _anchor in JOB_COLUMNS]
        status_index = columns.index("status")
        for item in items:
            if item in self.job_rows:
                self.job_rows[item]["status"] = status

            values = list(self.jobs_tree.item(item, "values"))
            if len(values) > status_index:
                values[status_index] = status
                self.jobs_tree.item(item, values=values)

        if self.current_source_url in source_url_set:
            self.current_status = status
            self._set_text_widget(self.detail_fields["status"], status)

    def _update_job_company_application_counts(
        self,
        counts: dict[str, str],
    ) -> None:
        columns = [name for name, _label, _width, _anchor in JOB_COLUMNS]
        company_index = columns.index("company")
        for item, row in self.job_rows.items():
            company_id = clean(row.get("company_id", ""))
            if company_id not in counts:
                continue
            count = counts[company_id]
            row["company_application_count"] = count
            values = list(self.jobs_tree.item(item, "values"))
            if len(values) > company_index:
                values[company_index] = format_company_display(
                    row.get("company", ""),
                    count,
                )
                self.jobs_tree.item(item, values=values)
        self._schedule_job_link_labels()

    def _selected_job_items(self) -> list[str]:
        return [item for item in self.jobs_tree.selection() if item in self.job_rows]

    def _detail_item_from_selection(self) -> str:
        selected = self._selected_job_items()
        if not selected:
            return ""

        focused = self.jobs_tree.focus()
        if focused in selected:
            return focused
        return selected[0]

    def _update_selected_job_scores(self, score: int, fit: int, interest: int) -> None:
        selected = self.jobs_tree.selection()
        if not selected:
            return

        item = selected[0]
        if not self._score_visible(score):
            self.jobs_tree.delete(item)
            self.job_rows.pop(item, None)
            self._clear_detail()
            return

        if item in self.job_rows:
            self.job_rows[item]["score"] = str(score)
            self.job_rows[item]["fit"] = str(fit)
            self.job_rows[item]["interest"] = str(interest)

        columns = [name for name, _label, _width, _anchor in JOB_COLUMNS]
        values = list(self.jobs_tree.item(item, "values"))
        for name, value in (
            ("score", str(score)),
            ("fit", str(fit)),
            ("interest", str(interest)),
        ):
            index = columns.index(name)
            if len(values) > index:
                values[index] = value
        self.jobs_tree.item(item, values=values)

    def _set_status_buttons_state(self, enabled: bool) -> None:
        state = tk.NORMAL if enabled else tk.DISABLED
        for button in self.status_buttons:
            button.configure(state=state)

    def _set_text_widget(self, widget: CopyableText, value: str) -> None:
        widget.set_value(value)

    def _widget_text(self, widget: CopyableText) -> str:
        return widget.get_value().strip()

    def _status_visible(self, status: str) -> bool:
        variable = self.status_filter_vars.get(status)
        return bool(variable and variable.get())

    def _score_visible(self, score: int) -> bool:
        return self.show_zero_var.get() or score != 0

    def _jobs_tree_sorted(self, column: str, descending: bool) -> None:
        self._close_title_selection()
        self.job_sort_column = column
        self.job_sort_descending = descending
        self._save_settings()
        self._schedule_job_link_labels()

    def _bind_score_field(self, widget: CopyableText) -> None:
        widget.bind("<KeyPress>", self._score_field_keypress)
        widget.bind("<Return>", self._save_scores_from_detail)
        widget.bind("<KP_Enter>", self._save_scores_from_detail)
        widget.bind("<FocusOut>", self._save_scores_from_detail)

    def _bind_editable_entry(self, widget: tk.Widget) -> None:
        widget.bind("<Control-a>", self._select_entry_text)
        widget.bind("<Control-A>", self._select_entry_text)
        widget.bind("<Control-c>", self._copy_widget_event)
        widget.bind("<Control-C>", self._copy_widget_event)
        widget.bind("<Control-x>", self._cut_entry_event)
        widget.bind("<Control-X>", self._cut_entry_event)
        widget.bind("<Control-v>", self._paste_entry_event)
        widget.bind("<Control-V>", self._paste_entry_event)
        widget.bind("<Shift-Insert>", self._paste_entry_event)
        widget.bind("<Control-Insert>", self._copy_widget_event)
        widget.bind("<Control-KeyPress>", self._entry_shortcut_event)
        widget.bind("<<Copy>>", self._copy_widget_event)
        widget.bind("<<Cut>>", self._cut_entry_event)
        widget.bind("<<Paste>>", self._paste_entry_event)
        widget.bind("<Button-3>", self._show_entry_menu)

    def _bind_global_copy_shortcuts(self) -> None:
        self.bind_all("<Control-c>", self._global_copy_event, add="+")
        self.bind_all("<Control-C>", self._global_copy_event, add="+")
        self.bind_all("<Control-Insert>", self._global_copy_event, add="+")
        self.bind_all("<Control-KeyPress>", self._global_copy_shortcut_event, add="+")
        self.bind_all("<<Copy>>", self._global_copy_event, add="+")
        self.bind_all("<Button-3>", self._show_global_copy_menu, add="+")

    def _copy_tree_selection(self, event: tk.Event[tk.Misc]) -> str:
        return self._copy_widget_selection(event.widget)

    def _copy_widget_event(self, event: tk.Event[tk.Misc]) -> str:
        return self._copy_widget_selection(event.widget)

    def _global_copy_event(self, event: tk.Event[tk.Misc]) -> str | None:
        widget = event.widget
        if self._copy_widget_text(widget):
            return "break"

        focused = self.focus_get()
        if focused is not None and focused is not widget:
            if self._copy_widget_text(focused):
                return "break"
        return None

    def _global_copy_shortcut_event(self, event: tk.Event[tk.Misc]) -> str | None:
        key = event.keysym.lower()
        keycode = int(getattr(event, "keycode", 0) or 0)
        if key in {"c", "insert"} or keycode in {45, 67}:
            return self._global_copy_event(event)
        return None

    def _show_global_copy_menu(self, event: tk.Event[tk.Misc]) -> str | None:
        if self._copyable_widget_text(event.widget) == "":
            return None

        widget = event.widget
        menu = tk.Menu(self, tearoff=False)
        menu.add_command(
            label="Copy",
            command=lambda widget=widget: self._copy_widget_text(widget),
        )
        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()
        return "break"

    def _copy_shortcut_event(self, event: tk.Event[tk.Misc]) -> str | None:
        key = event.keysym.lower()
        keycode = int(getattr(event, "keycode", 0) or 0)
        if key == "a" or keycode == 65:
            if isinstance(event.widget, tk.Text):
                return self._select_text(event)
            return self._select_entry_text(event)
        if key in {"c", "insert"} or keycode in {45, 67}:
            return self._copy_widget_selection(event.widget)
        return None

    def _entry_shortcut_event(self, event: tk.Event[tk.Misc]) -> str | None:
        key = event.keysym.lower()
        keycode = int(getattr(event, "keycode", 0) or 0)
        if key == "a" or keycode == 65:
            return self._select_entry_text(event)
        if key in {"c", "insert"} or keycode in {45, 67}:
            return self._copy_widget_selection(event.widget)
        if key == "x" or keycode == 88:
            return self._cut_entry_event(event)
        if key == "v" or keycode == 86:
            return self._paste_entry_event(event)
        return None

    def _copy_widget_selection(self, widget: tk.Misc) -> str:
        self._copy_widget_text(widget)
        return "break"

    def _copy_widget_text(self, widget: tk.Misc) -> bool:
        text = self._copyable_widget_text(widget)
        if not text:
            return False

        self.clipboard_clear()
        self.clipboard_append(text)
        self.update_idletasks()
        self.status_var.set("Copied")
        return True

    def _copyable_widget_text(self, widget: tk.Misc) -> str:
        if hasattr(widget, "selection") and hasattr(widget, "set"):
            return self._tree_selection_text(widget)
        elif isinstance(widget, tk.Text):
            return self._text_selection(widget)
        elif hasattr(widget, "selection_get") and hasattr(widget, "get"):
            return self._entry_selection(widget)

        return self._widget_config_text(widget)

    def _widget_config_text(self, widget: tk.Misc) -> str:
        for option in ("text", "label"):
            try:
                value = widget.cget(option)
            except tk.TclError:
                continue
            text = clean(value)
            if text:
                return text

        try:
            variable_name = clean(widget.cget("textvariable"))
        except tk.TclError:
            variable_name = ""
        if variable_name:
            try:
                return clean(self.getvar(variable_name))
            except tk.TclError:
                return ""
        return ""

    def _tree_selection_text(self, tree: tk.Misc) -> str:
        selected = tree.selection()
        if not selected and tree.focus():
            selected = (tree.focus(),)
        if not selected:
            return ""

        columns = tree["columns"]
        lines = [
            "\t".join(clean(tree.set(item, column)) for column in columns)
            for item in selected
        ]
        return "\n".join(lines)

    def _text_selection(self, widget: tk.Text) -> str:
        try:
            return widget.get(tk.SEL_FIRST, tk.SEL_LAST)
        except tk.TclError:
            return widget.get("1.0", "end-1c")

    def _entry_selection(self, widget: tk.Misc) -> str:
        try:
            return widget.selection_get()
        except tk.TclError:
            return clean(widget.get())

    def _paste_entry_event(self, event: tk.Event[tk.Misc]) -> str:
        widget = event.widget
        if not hasattr(widget, "insert") or not hasattr(widget, "delete"):
            return "break"
        try:
            text = self.clipboard_get()
        except tk.TclError:
            return "break"
        try:
            first = widget.index(tk.SEL_FIRST)
            last = widget.index(tk.SEL_LAST)
            widget.delete(first, last)
        except tk.TclError:
            pass
        widget.insert(tk.INSERT, text)
        return "break"

    def _cut_entry_event(self, event: tk.Event[tk.Misc]) -> str:
        widget = event.widget
        if not hasattr(widget, "delete"):
            return "break"
        text = self._entry_selection(widget)
        if text:
            self.clipboard_clear()
            self.clipboard_append(text)
            self.update_idletasks()
            try:
                first = widget.index(tk.SEL_FIRST)
                last = widget.index(tk.SEL_LAST)
                widget.delete(first, last)
            except tk.TclError:
                pass
        return "break"

    def _select_entry_text(self, event: tk.Event[tk.Misc]) -> str:
        widget = event.widget
        if hasattr(widget, "selection_range") and hasattr(widget, "icursor"):
            widget.selection_range(0, tk.END)
            widget.icursor(tk.END)
        return "break"

    def _show_entry_menu(self, event: tk.Event[tk.Misc]) -> str:
        widget = event.widget
        menu = tk.Menu(self, tearoff=False)
        menu.add_command(
            label="Cut",
            command=lambda widget=widget: self._cut_entry_event(
                self._widget_event(widget)
            ),
        )
        menu.add_command(
            label="Copy",
            command=lambda widget=widget: self._copy_widget_selection(widget),
        )
        menu.add_command(
            label="Paste",
            command=lambda widget=widget: self._paste_entry_event(
                self._widget_event(widget)
            ),
        )
        menu.add_command(
            label="Select all",
            command=lambda widget=widget: self._select_all_widget_text(widget),
        )
        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()
        return "break"

    def _widget_event(self, widget: tk.Misc) -> tk.Event[tk.Misc]:
        event = tk.Event()
        event.widget = widget
        return event

    def _select_text(self, event: tk.Event[tk.Misc]) -> str:
        widget = event.widget
        if isinstance(widget, tk.Text):
            widget.tag_add(tk.SEL, "1.0", "end-1c")
            widget.mark_set(tk.INSERT, "1.0")
            widget.see(tk.INSERT)
        return "break"

    def _show_copy_menu(self, event: tk.Event[tk.Misc]) -> str:
        widget = event.widget
        if hasattr(widget, "identify_row") and hasattr(widget, "selection_set"):
            item = widget.identify_row(event.y)
            if item:
                widget.selection_set(item)
                widget.focus(item)

        menu = tk.Menu(self, tearoff=False)
        menu.add_command(
            label="Copy",
            command=lambda widget=widget: self._copy_widget_selection(widget),
        )
        if not hasattr(widget, "selection") or not hasattr(widget, "set"):
            menu.add_command(
                label="Select all",
                command=lambda widget=widget: self._select_all_widget_text(widget),
            )
        try:
            menu.tk_popup(event.x_root, event.y_root)
        finally:
            menu.grab_release()
        return "break"

    def _select_all_widget_text(self, widget: tk.Misc) -> None:
        if isinstance(widget, tk.Text):
            widget.tag_add(tk.SEL, "1.0", "end-1c")
            widget.mark_set(tk.INSERT, "1.0")
            widget.see(tk.INSERT)
        elif hasattr(widget, "selection_range") and hasattr(widget, "icursor"):
            widget.selection_range(0, tk.END)
            widget.icursor(tk.END)

    def _block_readonly_text_edit(self, event: tk.Event[tk.Misc]) -> str | None:
        key = event.keysym.lower()
        ctrl_pressed = bool(event.state & 0x4)
        if ctrl_pressed and key == "a":
            return self._select_text(event)
        if ctrl_pressed:
            return None

        allowed_keys = {
            "left",
            "right",
            "up",
            "down",
            "home",
            "end",
            "prior",
            "next",
            "tab",
            "shift_l",
            "shift_r",
            "control_l",
            "control_r",
        }
        if key in allowed_keys:
            return None
        return "break"

    def _score_field_keypress(self, event: tk.Event[tk.Misc]) -> str | None:
        key = event.keysym.lower()
        ctrl_pressed = bool(event.state & 0x4)
        if ctrl_pressed:
            return None

        allowed_keys = {
            "backspace",
            "delete",
            "left",
            "right",
            "home",
            "end",
            "tab",
            "shift_l",
            "shift_r",
            "control_l",
            "control_r",
        }
        if key in allowed_keys:
            return None
        if key in {"return", "kp_enter"}:
            return self._save_scores_from_detail(event)
        if event.char and event.char.isdigit():
            return None
        return "break"

    def _break_event(self, _event: tk.Event[tk.Misc]) -> str:
        return "break"


if __name__ == "__main__":
    app = JobsViewer()
    app.mainloop()
