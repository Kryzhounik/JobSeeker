from __future__ import annotations

import sqlite3
import tkinter as tk
import webbrowser
from pathlib import Path
from tkinter import messagebox
from tkinter import ttk
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DB_PATH = ROOT / "data" / "jobs.sqlite"


JOB_COLUMNS = (
    ("score", "Score", 56, "center"),
    ("fit", "Fit", 48, "center"),
    ("interest", "Interest", 72, "center"),
    ("company", "Company", 160, "w"),
    ("title", "Title", 280, "w"),
    ("role", "Role", 120, "w"),
    ("seniority", "Seniority", 100, "w"),
    ("remote_scope", "Remote", 92, "w"),
    ("relocation", "Reloc", 72, "center"),
    ("location", "Location", 150, "w"),
    ("primary_language", "Language", 140, "w"),
    ("salary", "Salary", 150, "w"),
    ("added_at", "Added", 110, "w"),
)

DETAIL_FIELDS = (
    ("title", "Title"),
    ("company", "Company"),
    ("score", "Score"),
    ("fit", "Fit"),
    ("interest", "Interest"),
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


def db_uri() -> str:
    return f"{DB_PATH.as_uri()}?mode=ro"


def clean(value: Any) -> str:
    if value is None:
        return ""
    return str(value)


class JobsViewer(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("Seeker Jobs")
        self.geometry("1280x820")
        self.minsize(980, 650)

        self.job_rows: dict[str, dict[str, str]] = {}
        self.current_source_url = ""
        self.detail_vars: dict[str, tk.StringVar] = {}

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

        self.status_var = tk.StringVar(value="")
        ttk.Label(toolbar, textvariable=self.status_var, style="Muted.TLabel").grid(
            row=0,
            column=1,
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
            self.jobs_tree.heading(name, text=label)
            self.jobs_tree.column(name, width=width, minwidth=42, anchor=anchor, stretch=True)

        self.jobs_tree.tag_configure("odd", background="#f7f9fb")
        self.jobs_tree.bind("<<TreeviewSelect>>", self._on_job_selected)

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

        self.link_var = tk.StringVar(value="")
        self.link_label = ttk.Label(
            header,
            textvariable=self.link_var,
            foreground="#1f5da8",
            cursor="hand2",
        )
        self.link_label.grid(row=1, column=0, sticky="w", pady=(3, 0))
        self.link_label.bind("<Button-1>", self._open_current_link)

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
            var = tk.StringVar(value="")
            self.detail_vars[name] = var
            ttk.Label(parent, textvariable=var, wraplength=360).grid(
                row=row_index,
                column=1,
                sticky="ew",
                pady=3,
            )

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
            selectmode="none",
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
            self.tech_tree.heading(name, text=label)
            self.tech_tree.column(name, width=width, minwidth=52, anchor=anchor, stretch=True)

        self.tech_tree.tag_configure("odd", background="#f7f9fb")

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
        self.summary_text.configure(state=tk.DISABLED)

    def connect(self) -> sqlite3.Connection:
        if not DB_PATH.exists():
            raise FileNotFoundError(f"Database not found: {DB_PATH}")
        connection = sqlite3.connect(db_uri(), uri=True)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA query_only = ON")
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
        children = self.jobs_tree.get_children()
        if children:
            self.jobs_tree.selection_set(children[0])
            self.jobs_tree.focus(children[0])
            self.jobs_tree.see(children[0])

    def _load_jobs(self) -> list[sqlite3.Row]:
        with self.connect() as connection:
            return list(
                connection.execute(
                    """
                    SELECT
                        score,
                        fit,
                        interest,
                        company,
                        title,
                        role,
                        seniority,
                        remote_scope,
                        relocation,
                        location,
                        primary_language,
                        salary,
                        added_at,
                        source_url
                    FROM job_list
                    """
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
                    title,
                    company,
                    location,
                    remote_type,
                    remote_scope,
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
        title = detail.get("title") or "Untitled"
        company = detail.get("company", "")
        self.detail_title_var.set(f"{title} - {company}" if company else title)
        self.link_var.set(self.current_source_url)

        for name, _label in DETAIL_FIELDS:
            self.detail_vars[name].set(detail.get(name, ""))

        self.tech_tree.delete(*self.tech_tree.get_children())
        for index, row in enumerate(technologies):
            values = [clean(row[name]) for name, _label, _width, _anchor in TECH_COLUMNS]
            tags = ("odd",) if index % 2 else ()
            self.tech_tree.insert("", tk.END, values=values, tags=tags)

        self._set_summary(detail.get("summary", ""))

    def _clear_detail(self) -> None:
        self.detail_title_var.set("Select a job")
        self.link_var.set("")
        for var in self.detail_vars.values():
            var.set("")
        self.tech_tree.delete(*self.tech_tree.get_children())
        self._set_summary("")

    def _set_summary(self, value: str) -> None:
        self.summary_text.configure(state=tk.NORMAL)
        self.summary_text.delete("1.0", tk.END)
        if value:
            self.summary_text.insert("1.0", value)
        self.summary_text.configure(state=tk.DISABLED)

    def _open_current_link(self, _event: tk.Event[tk.Misc]) -> None:
        if self.current_source_url:
            webbrowser.open_new_tab(self.current_source_url)


if __name__ == "__main__":
    app = JobsViewer()
    app.mainloop()
