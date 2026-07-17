from __future__ import annotations

import sqlite3
import sys
import tkinter as tk
import webbrowser
from pathlib import Path
from tkinter import messagebox
from tkinter import ttk
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "Data" / "jobs.sqlite"
DRIVER_ROOT = ROOT / "Driver"
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.migrate import migrate_database


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
DEFAULT_STATUS_VALUES = ("New", "Checked", "Approved", "Closed")
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
    return (interest * fit + 50) // 100


class JobsViewer(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("Seeker Jobs")
        self.geometry("1280x820")
        self.minsize(980, 650)

        self.job_rows: dict[str, dict[str, str]] = {}
        self.current_source_url = ""
        self.current_status = ""
        self.current_fit = ""
        self.current_interest = ""
        self.detail_fields: dict[str, tk.Text] = {}
        self.status_buttons: list[ttk.Button] = []
        self.status_values = self._available_status_values()
        self.status_filter_vars = {
            status: tk.BooleanVar(value=True) for status in self.status_values
        }
        self.show_zero_var = tk.BooleanVar(value=False)
        self.id_search_var = tk.StringVar(value="")
        self.job_sort_column: str | None = None
        self.job_sort_descending = False
        self.tech_sort_column: str | None = None
        self.tech_sort_descending = False

        self._configure_style()
        self._build_ui()
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
        toolbar.columnconfigure(1, weight=1)

        refresh_button = ttk.Button(toolbar, text="Refresh", command=self.refresh_jobs)
        refresh_button.grid(row=0, column=0, sticky="w")

        filters = ttk.Frame(toolbar)
        filters.grid(row=0, column=1, sticky="w", padx=(8, 8))

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
                command=self.refresh_jobs,
            ).grid(row=0, column=column_index, sticky="w", padx=(0, 4))

        ttk.Checkbutton(
            filters,
            text="Show zero",
            variable=self.show_zero_var,
            command=self.refresh_jobs,
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
            column=2,
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

    def _build_jobs_table(self, parent: ttk.Frame) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(0, weight=1)

        columns = [name for name, _label, _width, _anchor in JOB_COLUMNS]
        self.jobs_tree = ttk.Treeview(
            parent,
            columns=columns,
            show="headings",
            selectmode="browse",
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

        for row in rows:
            status = clean(row[0])
            if status and status not in values:
                values.append(status)
        return tuple(values)

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
        self.job_sort_column = None
        self.job_sort_descending = False
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
                    WHERE j.source_url = jl.source_url
                        AND (
                            CAST(j.id AS TEXT) = ?
                            OR j.source_job_id = ?
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
                        score,
                        fit,
                        interest,
                        status,
                        remote_scope,
                        relocation,
                        location,
                        company,
                        title,
                        role,
                        seniority,
                        primary_language,
                        salary,
                        added_at,
                        source_url
                    FROM job_list jl
                    {where_sql}
                    """,
                    parameters,
                )
            )

    def _on_job_selected(self, _event: tk.Event[tk.Misc]) -> None:
        selected = self.jobs_tree.selection()
        if not selected:
            return

        row = self.job_rows.get(selected[0])
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
                    id,
                    source_job_id,
                    title,
                    company,
                    location,
                    remote_type,
                    remote_scope,
                    status,
                    relocation,
                    seniority,
                    role,
                    salary,
                    job_interest AS interest,
                    candidate_fit_percent AS fit,
                    CAST(ROUND(job_interest * candidate_fit_percent / 100.0) AS INTEGER)
                        AS score,
                    source_url,
                    summary,
                    added_at
                FROM jobs
                WHERE source_url = ?
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
                            WHEN 'required' THEN 'req'
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
                            WHEN 'required' THEN 1
                            WHEN 'nice_to_have' THEN 2
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

    def _set_current_status(self, status: str) -> None:
        if not self.current_source_url:
            return

        try:
            with self.connect_writable() as connection:
                result = connection.execute(
                    """
                    UPDATE jobs
                    SET status = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE source_url = ?
                    """,
                    (status, self.current_source_url),
                )
                if result.rowcount != 1:
                    raise KeyError(f"Job not found: {self.current_source_url}")
        except Exception as error:
            messagebox.showerror("Status update failed", str(error))
            self.status_var.set("Status update failed")
            return

        self.current_status = status
        self._set_text_widget(self.detail_fields["status"], status)
        self._update_selected_job_status(status)
        self.status_var.set(f"Status -> {status}")

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

    def _update_selected_job_status(self, status: str) -> None:
        selected = self.jobs_tree.selection()
        if not selected:
            return

        item = selected[0]
        if not self._status_visible(status):
            self.jobs_tree.delete(item)
            self.job_rows.pop(item, None)
            self._clear_detail()
            return

        if item in self.job_rows:
            self.job_rows[item]["status"] = status

        columns = [name for name, _label, _width, _anchor in JOB_COLUMNS]
        status_index = columns.index("status")
        values = list(self.jobs_tree.item(item, "values"))
        if len(values) > status_index:
            values[status_index] = status
            self.jobs_tree.item(item, values=values)

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
        descending = not current_descending if current_column == column else column in numeric_columns
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
        return column, descending

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

    def _copy_tree_selection(self, event: tk.Event[tk.Misc]) -> str:
        return self._copy_widget_selection(event.widget)

    def _copy_widget_event(self, event: tk.Event[tk.Misc]) -> str:
        return self._copy_widget_selection(event.widget)

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

    def _copy_widget_selection(self, widget: tk.Misc) -> str:
        if hasattr(widget, "selection") and hasattr(widget, "set"):
            text = self._tree_selection_text(widget)
        elif isinstance(widget, tk.Text):
            text = self._text_selection(widget)
        elif hasattr(widget, "selection_get") and hasattr(widget, "get"):
            text = self._entry_selection(widget)
        else:
            return "break"

        if text:
            self.clipboard_clear()
            self.clipboard_append(text)
            self.update_idletasks()
            self.status_var.set("Copied")
        return "break"

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

    def _select_entry_text(self, event: tk.Event[tk.Misc]) -> str:
        widget = event.widget
        if hasattr(widget, "selection_range") and hasattr(widget, "icursor"):
            widget.selection_range(0, tk.END)
            widget.icursor(tk.END)
        return "break"

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
