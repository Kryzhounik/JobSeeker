from __future__ import annotations

import json
import queue
import re
import sqlite3
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
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
DRIVER_ROOT = ROOT / "Driver"
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

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


def db_uri() -> str:
    return f"{DB_PATH.as_uri()}?mode=ro"


def ensure_database_schema() -> None:
    migrate_database(db_path=DB_PATH)


def clean(value: Any) -> str:
    if value is None:
        return ""
    return str(value)


def calculate_score(fit: int, interest: int) -> int:
    return (interest * fit * fit + 5000) // 10000


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
        self.detail_fields: dict[str, tk.Text] = {}
        self.status_buttons: list[ttk.Button] = []
        self.availability_button: ttk.Button | None = None
        self.refilter_button: ttk.Button | None = None
        self.refilter_detail_button: ttk.Button | None = None
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
        self.job_sort_column = self._saved_job_sort_column()
        self.job_sort_descending = self._saved_job_sort_descending()
        self.tech_sort_column: str | None = None
        self.tech_sort_descending = False

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

        toolbar = ttk.Frame(self, padding=(10, 8))
        toolbar.grid(row=0, column=0, sticky="ew")
        toolbar.columnconfigure(3, weight=1)

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

        filters = ttk.Frame(toolbar)
        filters.grid(row=0, column=3, sticky="w", padx=(8, 8))

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

        search_column = len(self.status_values) + 2
        ttk.Label(filters, text="ID", style="Muted.TLabel").grid(
            row=0,
            column=search_column,
            sticky="w",
            padx=(10, 4),
        )
        id_entry = ttk.Entry(filters, textvariable=self.id_search_var, width=16)
        id_entry.grid(row=0, column=search_column + 1, sticky="w")
        self._bind_editable_entry(id_entry)
        id_entry.bind("<Return>", self._refresh_from_event)
        ttk.Button(filters, text="Search", command=self.refresh_jobs).grid(
            row=0,
            column=search_column + 2,
            sticky="w",
            padx=(4, 0),
        )
        ttk.Button(filters, text="Clear", command=self._clear_id_search).grid(
            row=0,
            column=search_column + 3,
            sticky="w",
            padx=(4, 0),
        )

        self.status_var = tk.StringVar(value="")
        ttk.Label(toolbar, textvariable=self.status_var, style="Muted.TLabel").grid(
            row=0,
            column=4,
            sticky="e",
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
        self.refilter_detail_button = ttk.Button(
            footer,
            text="Refilter detail",
            command=self._start_refilter_detail,
        )
        self.refilter_detail_button.grid(row=0, column=0, sticky="w")

    def _build_jobs_table(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(0, weight=1)

        columns = [name for name, _label, _width, _anchor in JOB_COLUMNS]
        self.jobs_tree = ttk.Treeview(
            parent,
            columns=columns,
            show="headings",
            selectmode="extended",
        )
        self.jobs_tree.grid(row=0, column=0, sticky="nsew")

        y_scroll = ttk.Scrollbar(
            parent,
            orient=tk.VERTICAL,
            command=self.jobs_tree.yview,
        )
        y_scroll.grid(row=0, column=1, sticky="ns")
        x_scroll = ttk.Scrollbar(
            parent,
            orient=tk.HORIZONTAL,
            command=self.jobs_tree.xview,
        )
        x_scroll.grid(row=1, column=0, sticky="ew")
        self.jobs_tree.configure(yscrollcommand=y_scroll.set, xscrollcommand=x_scroll.set)

        for name, label, width, anchor in JOB_COLUMNS:
            self.jobs_tree.heading(
                name,
                text=label,
                command=lambda column=name: self._sort_jobs_tree(column),
            )
            self.jobs_tree.column(name, width=width, minwidth=42, anchor=anchor, stretch=True)

        self.jobs_tree.tag_configure("odd", background="#f7f9fb")
        self.jobs_tree.bind("<<TreeviewSelect>>", self._on_job_selected)
        self.jobs_tree.bind("<Control-c>", self._copy_tree_selection)
        self.jobs_tree.bind("<Control-C>", self._copy_tree_selection)
        self.jobs_tree.bind("<Control-Insert>", self._copy_tree_selection)
        self.jobs_tree.bind("<<Copy>>", self._copy_tree_selection)
        self.jobs_tree.bind("<Button-3>", self._show_copy_menu)

    def _build_detail(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(1, weight=1)

        header = ttk.Frame(parent, padding=(0, 8, 0, 6))
        header.grid(row=0, column=0, sticky="ew")
        header.columnconfigure(0, weight=1)

        self.detail_title_var = tk.StringVar(value="Select a job")
        ttk.Label(header, textvariable=self.detail_title_var, style="Title.TLabel").grid(
            row=0,
            column=0,
            sticky="w",
        )

        link_frame = ttk.Frame(header)
        link_frame.grid(row=1, column=0, sticky="ew", pady=(3, 0))
        link_frame.columnconfigure(0, weight=1)

        self.link_text = tk.Text(
            link_frame,
            height=1,
            wrap="none",
            borderwidth=1,
            relief="solid",
            padx=4,
            pady=2,
        )
        self.link_text.grid(row=0, column=0, sticky="ew")
        self._bind_copyable_text(self.link_text)

        ttk.Button(link_frame, text="Open", command=self._open_current_link).grid(
            row=0,
            column=1,
            sticky="e",
            padx=(8, 0),
        )

        status_frame = ttk.Frame(header)
        status_frame.grid(row=2, column=0, sticky="ew", pady=(6, 0))
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

        body = ttk.PanedWindow(parent, orient=tk.HORIZONTAL)
        body.grid(row=1, column=0, sticky="nsew")

        meta_frame = ttk.Frame(body, padding=(0, 0, 8, 0))
        tech_frame = ttk.Frame(body, padding=(8, 0, 0, 0))
        body.add(meta_frame, weight=1)
        body.add(tech_frame, weight=2)

        self._build_meta(meta_frame)
        self._build_tech_and_summary(tech_frame)

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
            field = tk.Text(
                parent,
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
                self._bind_copyable_text(field)
            self.detail_fields[name] = field

    def _build_tech_and_summary(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(0, weight=3)
        parent.rowconfigure(2, weight=2)

        tech_box = ttk.Frame(parent)
        tech_box.grid(row=0, column=0, sticky="nsew")
        tech_box.columnconfigure(0, weight=1)
        tech_box.rowconfigure(0, weight=1)

        columns = [name for name, _label, _width, _anchor in TECH_COLUMNS]
        self.tech_tree = ttk.Treeview(
            tech_box,
            columns=columns,
            show="headings",
            selectmode="browse",
        )
        self.tech_tree.grid(row=0, column=0, sticky="nsew")

        tech_scroll = ttk.Scrollbar(
            tech_box,
            orient=tk.VERTICAL,
            command=self.tech_tree.yview,
        )
        tech_scroll.grid(row=0, column=1, sticky="ns")
        self.tech_tree.configure(yscrollcommand=tech_scroll.set)

        for name, label, width, anchor in TECH_COLUMNS:
            self.tech_tree.heading(
                name,
                text=label,
                command=lambda column=name: self._sort_tech_tree(column),
            )
            self.tech_tree.column(name, width=width, minwidth=52, anchor=anchor, stretch=True)

        self.tech_tree.tag_configure("odd", background="#f7f9fb")
        self.tech_tree.bind("<Control-c>", self._copy_tree_selection)
        self.tech_tree.bind("<Control-C>", self._copy_tree_selection)
        self.tech_tree.bind("<Control-Insert>", self._copy_tree_selection)
        self.tech_tree.bind("<<Copy>>", self._copy_tree_selection)
        self.tech_tree.bind("<Button-3>", self._show_copy_menu)

        ttk.Label(parent, text="Summary", style="Muted.TLabel").grid(
            row=1,
            column=0,
            sticky="w",
            pady=(8, 3),
        )

        self.summary_text = tk.Text(
            parent,
            height=8,
            wrap="word",
            borderwidth=1,
            relief="solid",
            padx=8,
            pady=6,
        )
        self.summary_text.grid(row=2, column=0, sticky="nsew")
        self.summary_text.bind("<KeyPress>", self._block_readonly_text_edit)
        self.summary_text.bind("<<Paste>>", self._break_event)
        self.summary_text.bind("<<Cut>>", self._break_event)
        self._bind_copyable_text(self.summary_text)

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
            values = [clean(row[name]) for name, _label, _width, _anchor in JOB_COLUMNS]
            tags = ("odd",) if index % 2 else ()
            self.jobs_tree.insert("", tk.END, iid=item_id, values=values, tags=tags)
            self.job_rows[item_id] = {key: clean(row[key]) for key in row.keys()}

        self.status_var.set(f"{len(rows)} jobs")
        if self.job_sort_column:
            self._apply_tree_sort(
                self.jobs_tree,
                JOB_NUMERIC_COLUMNS,
                self.job_sort_column,
                self.job_sort_descending,
            )
        self._update_tree_headings(
            self.jobs_tree,
            JOB_COLUMNS,
            self._sort_jobs_tree,
            self.job_sort_column,
            self.job_sort_descending,
        )
        children = self.jobs_tree.get_children()
        if children:
            self.jobs_tree.selection_set(children[0])
            self.jobs_tree.focus(children[0])
            self.jobs_tree.see(children[0])

    def _refresh_from_event(self, _event: tk.Event[tk.Misc]) -> str:
        self.refresh_jobs()
        return "break"

    def _clear_id_search(self) -> None:
        self.id_search_var.set("")
        self.refresh_jobs()

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
                        coalesce(j.candidate_fit_reason_code, '')
                            AS candidate_fit_reason_code,
                        coalesce(j.candidate_fit_reason, '')
                            AS candidate_fit_reason
                    FROM job_list jl
                    JOIN jobs j ON j.source_url = jl.source_url
                    {where_sql}
                    """,
                    parameters,
                )
            )

    def _on_job_selected(self, _event: tk.Event[tk.Misc]) -> None:
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
                    j.company,
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
                    j.added_at
                FROM jobs j
                JOIN source_jobs sj ON sj.id = j.source_job_ref
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
        title = detail.get("title") or "Untitled"
        company = detail.get("company", "")
        self.detail_title_var.set(f"{title} - {company}" if company else title)
        self._set_text_widget(self.link_text, self.current_source_url)

        for name, _label in DETAIL_FIELDS:
            self._set_text_widget(self.detail_fields[name], detail.get(name, ""))
        self._set_status_buttons_state(True)

        self.tech_tree.delete(*self.tech_tree.get_children())
        self.tech_sort_column = None
        self.tech_sort_descending = False
        for index, row in enumerate(technologies):
            values = [clean(row[name]) for name, _label, _width, _anchor in TECH_COLUMNS]
            tags = ("odd",) if index % 2 else ()
            self.tech_tree.insert("", tk.END, values=values, tags=tags)
        self._update_tree_headings(
            self.tech_tree,
            TECH_COLUMNS,
            self._sort_tech_tree,
            self.tech_sort_column,
            self.tech_sort_descending,
        )

        self._set_summary(detail.get("summary", ""))

    def _clear_detail(self) -> None:
        self.detail_title_var.set("Select a job")
        self._set_text_widget(self.link_text, "")
        self.current_status = ""
        self.current_fit = ""
        self.current_interest = ""
        for field in self.detail_fields.values():
            self._set_text_widget(field, "")
        self._set_status_buttons_state(False)
        self.tech_tree.delete(*self.tech_tree.get_children())
        self.tech_sort_column = None
        self.tech_sort_descending = False
        self._update_tree_headings(
            self.tech_tree,
            TECH_COLUMNS,
            self._sort_tech_tree,
            self.tech_sort_column,
            self.tech_sort_descending,
        )
        self._set_summary("")

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
            ("ID", 9, "center"),
            ("Title", 30, "left"),
            ("Fit", 7, "center"),
            ("Original", 74, "left"),
            ("Match", 24, "left"),
        )
        headers: list[tk.Label] = []
        for column_index, (label, width, justify) in enumerate(columns):
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
            headers.append(header)

        row_cells: list[tk.Text] = []

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
                    for (_label, width, _justify), value in zip(columns, values)
                )
                for column_index, ((_label, width, justify), value) in enumerate(
                    zip(columns, values)
                ):
                    cell = tk.Text(
                        rows_frame,
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
                    self._bind_copyable_text(cell)
                    bind_canvas_wheel(cell)
                    if column_index == 1:
                        cell.configure(foreground="#005a9c")
                        bind_source_link(
                            cell,
                            clean(candidate.get("source_url", "")),
                        )
                    row_cells.append(cell)

        fit_descending = False

        def sort_by_fit(_event: tk.Event[tk.Misc] | None = None) -> None:
            nonlocal fit_descending
            fit_descending = not fit_descending
            headers[2].configure(text="Fit v" if fit_descending else "Fit ^")
            displayed = sorted(
                candidates,
                key=lambda candidate: int(candidate.get("fit") or 0),
                reverse=fit_descending,
            )
            render_candidates(displayed)
            dialog.after_idle(lambda: canvas.yview_moveto(0))

        headers[2].configure(cursor="hand2")
        headers[2].bind("<ButtonRelease-1>", sort_by_fit)
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
                    j.company,
                    {score_sql} AS score
                FROM jobs j
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
            if error.code == 429:
                return "error", "LinkedIn returned 429; stopped to avoid rate limit."
            return "error", f"LinkedIn returned HTTP {error.code}."
        except urllib.error.URLError as error:
            return "error", f"Network error: {error.reason}"
        except TimeoutError:
            return "error", "Request timed out."

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
        except Exception as error:
            messagebox.showerror("Status update failed", str(error))
            self.status_var.set("Status update failed")
            return

        self._update_selected_jobs_status(status, items, source_urls)
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

    def _set_text_widget(self, widget: tk.Text, value: str) -> None:
        widget.delete("1.0", tk.END)
        if value:
            widget.insert("1.0", value)
        widget.mark_set(tk.INSERT, "1.0")

    def _widget_text(self, widget: tk.Text) -> str:
        return widget.get("1.0", "end-1c").strip()

    def _status_visible(self, status: str) -> bool:
        variable = self.status_filter_vars.get(status)
        return bool(variable and variable.get())

    def _score_visible(self, score: int) -> bool:
        return self.show_zero_var.get() or score != 0

    def _sort_jobs_tree(self, column: str) -> None:
        self.job_sort_column, self.job_sort_descending = self._sort_tree(
            self.jobs_tree,
            JOB_COLUMNS,
            JOB_NUMERIC_COLUMNS,
            column,
            self.job_sort_column,
            self.job_sort_descending,
        )
        self._save_settings()
        self._update_tree_headings(
            self.jobs_tree,
            JOB_COLUMNS,
            self._sort_jobs_tree,
            self.job_sort_column,
            self.job_sort_descending,
        )

    def _sort_tech_tree(self, column: str) -> None:
        self.tech_sort_column, self.tech_sort_descending = self._sort_tree(
            self.tech_tree,
            TECH_COLUMNS,
            TECH_NUMERIC_COLUMNS,
            column,
            self.tech_sort_column,
            self.tech_sort_descending,
        )
        self._update_tree_headings(
            self.tech_tree,
            TECH_COLUMNS,
            self._sort_tech_tree,
            self.tech_sort_column,
            self.tech_sort_descending,
        )

    def _sort_tree(
        self,
        tree: ttk.Treeview,
        columns: tuple[tuple[str, str, int, str], ...],
        numeric_columns: set[str],
        column: str,
        current_column: str | None,
        current_descending: bool,
    ) -> tuple[str, bool]:
        descending = (
            not current_descending
            if current_column == column
            else column in numeric_columns
        )
        self._apply_tree_sort(tree, numeric_columns, column, descending)
        return column, descending

    def _apply_tree_sort(
        self,
        tree: ttk.Treeview,
        numeric_columns: set[str],
        column: str,
        descending: bool,
    ) -> None:
        items = list(tree.get_children(""))

        def sort_key(item: str) -> Any:
            value = tree.set(item, column)
            if column == "level":
                return LEVEL_SORT_VALUES.get(value.casefold(), 0)
            if column in numeric_columns:
                try:
                    return float(value)
                except ValueError:
                    return float("-inf")
            return value.casefold()

        items.sort(key=sort_key, reverse=descending)
        for index, item in enumerate(items):
            tree.move(item, "", index)
        self._retag_tree(tree)

    def _update_tree_headings(
        self,
        tree: ttk.Treeview,
        columns: tuple[tuple[str, str, int, str], ...],
        sort_command: Any,
        sort_column: str | None,
        descending: bool,
    ) -> None:
        marker = " v" if descending else " ^"
        for name, label, _width, _anchor in columns:
            text = f"{label}{marker}" if name == sort_column else label
            tree.heading(
                name,
                text=text,
                command=lambda column=name: sort_command(column),
            )

    def _retag_tree(self, tree: ttk.Treeview) -> None:
        for index, item in enumerate(tree.get_children("")):
            tree.item(item, tags=("odd",) if index % 2 else ())

    def _bind_copyable_text(self, widget: tk.Text) -> None:
        widget.bind("<Control-a>", self._select_text)
        widget.bind("<Control-A>", self._select_text)
        widget.bind("<Control-c>", self._copy_widget_event)
        widget.bind("<Control-C>", self._copy_widget_event)
        widget.bind("<Control-Insert>", self._copy_widget_event)
        widget.bind("<Control-KeyPress>", self._copy_shortcut_event)
        widget.bind("<<Copy>>", self._copy_widget_event)
        widget.bind("<<Cut>>", self._break_event)
        widget.bind("<<Paste>>", self._break_event)
        widget.bind("<KeyPress>", self._block_readonly_text_edit)
        widget.bind("<Button-3>", self._show_copy_menu)

    def _bind_score_field(self, widget: tk.Text) -> None:
        widget.bind("<Control-a>", self._select_text)
        widget.bind("<Control-A>", self._select_text)
        widget.bind("<Control-c>", self._copy_widget_event)
        widget.bind("<Control-C>", self._copy_widget_event)
        widget.bind("<Control-Insert>", self._copy_widget_event)
        widget.bind("<Control-KeyPress>", self._copy_shortcut_event)
        widget.bind("<<Copy>>", self._copy_widget_event)
        widget.bind("<<Cut>>", self._break_event)
        widget.bind("<<Paste>>", self._break_event)
        widget.bind("<KeyPress>", self._score_field_keypress)
        widget.bind("<Return>", self._save_scores_from_detail)
        widget.bind("<KP_Enter>", self._save_scores_from_detail)
        widget.bind("<FocusOut>", self._save_scores_from_detail)
        widget.bind("<Button-3>", self._show_copy_menu)

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
