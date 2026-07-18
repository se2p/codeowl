import { useParams, useLocation, useNavigate, Link } from "react-router-dom";
import { usePageTitle } from "../hooks/usePageTitle.ts";
import { useAuth } from "../context/AuthContext.tsx";

export default function HelpPage() {
    usePageTitle("Help Page");
    const { context } = useParams<{ context?: string }>();
    const location = useLocation();
    const navigate = useNavigate();
    const { isTeacher, isAdmin } = useAuth();

    const searchParams = new URLSearchParams(location.search);
    const fromParam = searchParams.get("from");
    const isTeacherMode = isTeacher || isAdmin;

    const goBack = () => {
        if (fromParam) {
            const decoded = decodeURIComponent(fromParam);
            navigate(decoded);
        } else navigate("/");
    };

    // =====================================================================
    // FULL OVERVIEW PAGE (no context param)
    // =====================================================================
    if (!context) {
        return (
            <div className="container py-4" style={{ maxWidth: 960 }}>
                <h1 className="mb-2">🦉 CodeOwl Help Center</h1>
                <p className="lead text-muted mb-4">
                    Everything you need to know about CodeOwl v2 — generating, reviewing, and solving
                    tiered Parsons problems in a classroom context.
                </p>

                <hr className="mb-4" />

                {/* ---- GETTING STARTED ---- */}
                <h5 className="text-uppercase text-muted mb-3" style={{ letterSpacing: 1, fontSize: "0.75rem" }}>
                    Getting Started
                </h5>

                <section className="mb-4">
                    <h2>🔐 Accounts &amp; Authentication</h2>
                    <p>
                        CodeOwl v2 has full user accounts with three roles: <strong>Teacher</strong>, <strong>Student</strong>,
                        and <strong>Admin</strong>. Teachers self-register; students are enrolled by their teacher via
                        a one-time token link (no email required on day one).
                    </p>
                    <ul>
                        <li><strong>Login</strong> — Email + password, or token link (for students).</li>
                        <li><strong>Register</strong> — Teachers only. Students are created by the teacher.</li>
                        <li><strong>Forgot Password</strong> — Email-based reset flow.</li>
                        <li><strong>Onboarding</strong> — Students set their own password after first login.</li>
                        <li><strong>Profile</strong> — Change email or password at any time.</li>
                    </ul>
                    <Link to="/help/auth" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <section className="mb-4">
                    <h2>🏠 Home Page — Generate Sequences</h2>
                    <p>
                        The starting point for creating tiered Parsons problem sets. Provide code, a problem
                        statement, and/or concepts. CodeOwl generates three (or five) task tiers automatically.
                    </p>
                    <ul>
                        <li><strong>Generate Sequence</strong> — creates a new 3-tier (or 5-tier) Parsons problem set.</li>
                        <li><strong>Constraints</strong> — blacklist/whitelist constructs, control difficulty slope.</li>
                        <li><strong>Target Language</strong> — force problem statements in German or English.</li>
                        <li><strong>Demo / Reset</strong> — load a sample or clear the form.</li>
                        <li><strong>Shortcut</strong> — <kbd>Ctrl</kbd>+<kbd>Enter</kbd> to generate.</li>
                    </ul>
                    <Link to="/help/home" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <section className="mb-4">
                    <h2>✏️ Manual Task Creator</h2>
                    <p>
                        No LLM involved — write your own problem statements and code solutions directly.
                        Ideal when you want full control over the exact wording and code, or when you already
                        have classroom examples you want to turn into Parsons puzzles.
                    </p>
                    <ul>
                        <li><strong>Tier Editor</strong> — write up to 3 tiers (T1–T3); each tier gets its own statement and code.</li>
                        <li><strong>Live Block Preview</strong> — see how the code will be split into draggable blocks as you type.</li>
                        <li><strong>Tab key</strong> — inserts 4 spaces in the code editor (no focus-trap).</li>
                    </ul>
                    <Link to="/help/manual-create" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <hr className="my-4" />
                {/* ---- CLASSROOM MANAGEMENT ---- */}
                <h5 className="text-uppercase text-muted mb-3" style={{ letterSpacing: 1, fontSize: "0.75rem" }}>
                    Classroom Management
                </h5>

                <section className="mb-4">
                    <h2>🏫 Classes</h2>
                    <p>
                        Teachers create classes, manage student accounts within them, and assign worksheets.
                        Students are enrolled via a generated token link — no email required at sign-up.
                    </p>
                    <ul>
                        <li><strong>Create Class</strong> — name your class and add students.</li>
                        <li><strong>Student Tokens</strong> — one-time login links for each student.</li>
                        <li><strong>Assign Worksheets</strong> — attach worksheets to a class so students can access them.</li>
                        <li><strong>Tracking</strong> — enable per-class tracking to log detailed learning events for research.</li>
                    </ul>
                    <Link to="/help/classes" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <section className="mb-4">
                    <h2>🎓 My Classes (Students)</h2>
                    <p>
                        Students see their assigned classes and worksheets here. Each class card links to
                        the worksheets the teacher has assigned — click a worksheet to start solving tasks.
                    </p>
                    <ul>
                        <li>Click a class card to see its worksheets.</li>
                        <li>Click a worksheet to open it and see all tasks.</li>
                        <li>If your class list is empty, ask your teacher to enrol you.</li>
                    </ul>
                    <Link to="/help/my-classes" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <section className="mb-4">
                    <h2>📝 Worksheets</h2>
                    <p>
                        Worksheets group sequences into a coherent unit (e.g., "Week 3 — Arrays"). Teachers
                        assign worksheets to classes, configure hint settings, and export them to PDF.
                    </p>
                    <ul>
                        <li><strong>Manage Sequences</strong> — add or remove sequences from a worksheet.</li>
                        <li><strong>Hint Configuration</strong> — enable/disable hints, set credits per task.</li>
                        <li><strong>Assign to Class</strong> — make the worksheet visible to students.</li>
                        <li><strong>Export</strong> — PDF (puzzle or free-form) and CSV/JSON export.</li>
                    </ul>
                    <Link to="/help/worksheets" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <hr className="my-4" />
                {/* ---- FINDING & REUSING CONTENT ---- */}
                <h5 className="text-uppercase text-muted mb-3" style={{ letterSpacing: 1, fontSize: "0.75rem" }}>
                    Finding &amp; Reusing Content
                </h5>

                <section className="mb-4">
                    <h2>📚 Library</h2>
                    <p>
                        Browse, search, and reuse all sequences available to you. Filter by programming language,
                        concept tags, tier count, or full-text search. Copy any sequence into your own worksheet
                        with one click.
                    </p>
                    <ul>
                        <li><strong>Full-Text Search</strong> — searches problem statements and code simultaneously.</li>
                        <li><strong>Tags</strong> — structural tags (e.g., <code>for-loop</code>) and semantic tags (e.g., <code>fibonacci</code>).</li>
                        <li><strong>One-Click Copy</strong> — copy a sequence directly into one of your worksheets.</li>
                        <li><strong>URL-State Filters</strong> — shareable filter links.</li>
                    </ul>
                    <Link to="/help/library" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <section className="mb-4">
                    <h2>🎓 Curriculum</h2>
                    <p>
                        Browse the Bavarian LehrplanPLUS (G9 Informatik) topic tree and generate sequences
                        directly from a curriculum topic. CodeOwl automatically fills in concepts and injects
                        curated reference code for the chosen grade level.
                    </p>
                    <ul>
                        <li><strong>Topic Tree</strong> — grades 7, 9, 10, 11, 12/13 with learning areas.</li>
                        <li><strong>Auto-Context</strong> — concepts and code examples pre-filled from the topic.</li>
                        <li><strong>Grade Calibration</strong> — the prompt adapts difficulty to the selected grade.</li>
                    </ul>
                    <Link to="/help/curriculum" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <hr className="my-4" />
                {/* ---- SEQUENCES & TASKS ---- */}
                <h5 className="text-uppercase text-muted mb-3" style={{ letterSpacing: 1, fontSize: "0.75rem" }}>
                    Sequences &amp; Tasks
                </h5>

                <section className="mb-4">
                    <h2>📋 Sequence Page</h2>
                    <p>
                        Overview of all tasks (T1–T3 or T1–T5) within a generated sequence. Teachers see
                        model solutions, can edit tasks, and export the sequence.
                    </p>
                    <ul>
                        <li><strong>Solve Task / Review Task</strong> — student or teacher solver view.</li>
                        <li><strong>Edit Task</strong> — adjust problem statement and code (teacher only).</li>
                        <li><strong>Share Sequence</strong> — copies a student-safe link to clipboard.</li>
                        <li><strong>Export</strong> — PDF or JSON/CSV download.</li>
                    </ul>
                    <Link to="/help/sequence" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <section className="mb-4">
                    <h2>🧩 Solve Page</h2>
                    <p>
                        The interactive Parsons problem solver. Drag, click, or use keyboard to arrange
                        shuffled code blocks, then request feedback.
                    </p>
                    <ul>
                        <li><strong>Feedback</strong> — green / yellow / red per block.</li>
                        <li><strong>Click-to-Move</strong> — alternative to drag-and-drop (great for trackpads).</li>
                        <li><strong>Distractors</strong> — plausible but incorrect blocks that must be identified and excluded.</li>
                        <li><strong>Hints</strong> — static-analysis-powered hints that highlight problematic blocks.</li>
                        <li><strong>Completion Banner</strong> — "✓ Solved!" with stats when correct.</li>
                    </ul>
                    <Link to="/help/solve" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <hr className="my-4" />
                {/* ---- ADVANCED FEATURES ---- */}
                <h5 className="text-uppercase text-muted mb-3" style={{ letterSpacing: 1, fontSize: "0.75rem" }}>
                    Advanced Features
                </h5>

                <section className="mb-4">
                    <h2>💡 Hint System</h2>
                    <p>
                        When a student is stuck, the hint system runs static analysis on the current block
                        arrangement and highlights the problematic blocks — no guesswork, no hallucination.
                        A second tier optionally calls the LLM for a student-friendly explanation.
                    </p>
                    <ul>
                        <li><strong>Level 1</strong> — deterministic: Ruff (Python) or javac/SpotBugs (Java) identifies the error.</li>
                        <li><strong>Level 2</strong> — "Explain in detail": the LLM explains the finding in plain language.</li>
                        <li><strong>Credits</strong> — configurable per worksheet (default: 5 per task).</li>
                    </ul>
                    <Link to="/help/hints" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <section className="mb-4">
                    <h2>🎭 Distractors</h2>
                    <p>
                        Distractor blocks are plausible-looking but incorrect code lines mixed into the
                        puzzle pool. Students must identify and exclude them. Taxonomy-based (6 types),
                        LLM-generated by a separate "Antagonist" call.
                    </p>
                    <ul>
                        <li><strong>Types</strong> — Logic Flip, Off-by-One, Variable Mismatch, Boundary Identity, Structural Illogical, Redundant Zombie.</li>
                        <li><strong>Teacher Controls</strong> — edit, regenerate, or disable distractors per task.</li>
                        <li><strong>Hint Integration</strong> — hints detect distractor blocks first before doing full analysis.</li>
                    </ul>
                    <Link to="/help/distractors" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                <section className="mb-5">
                    <h2>👩‍🏫 Teacher Mode</h2>
                    <p>
                        Teachers and admins automatically have access to Teacher Mode — model solutions,
                        review tools, edit controls, and export options.
                    </p>
                    <ul>
                        <li>Toggle the "Lehrer-Ansicht" button to reveal/hide model solutions.</li>
                        <li>Edit tasks, manage distractors, configure hints per worksheet.</li>
                        <li>Students never see teacher controls regardless of URL.</li>
                    </ul>
                    <Link to="/help/teacher" className="btn btn-sm btn-outline-secondary mt-1">
                        Learn more →
                    </Link>
                </section>

                {isAdmin && (
                    <>
                        <hr className="my-4" />
                        <h5 className="text-uppercase text-muted mb-3" style={{ letterSpacing: 1, fontSize: "0.75rem" }}>
                            Admin Tools
                        </h5>
                        <section className="mb-5">
                            <h2>🔒 Admin Panel</h2>
                            <p>
                                Admin-only tools for managing the platform: user accounts, tag hygiene, and
                                bulk operations on library content.
                            </p>
                            <ul>
                                <li><strong>User Management</strong> — view, edit roles, and delete user accounts.</li>
                                <li><strong>Library Admin</strong> — merge duplicate tags, bulk-retag sequences after model updates.</li>
                            </ul>
                            <Link to="/help/admin" className="btn btn-sm btn-outline-danger mt-1">
                                Learn more →
                            </Link>
                        </section>
                    </>
                )}

                <hr className="my-4" />
                <button onClick={goBack} className="btn btn-outline-secondary">
                    ← Back
                </button>
            </div>
        );
    }

    // =====================================================================
    // CONTEXT-SPECIFIC HELP
    // =====================================================================
    const renderContent = () => {
        switch (context) {

            // ------------------------------------------------------------------
            case "auth":
                return (
                    <>
                        <h2>🔐 Accounts &amp; Authentication</h2>
                        <p>
                            CodeOwl v2 has full user accounts with three roles. All learning data (events,
                            feedback logs) is linked to a user ID where available; anonymous use is still
                            supported.
                        </p>

                        <h5>👥 Roles</h5>
                        <ul>
                            <li><strong>Teacher</strong> — can generate sequences, manage classes, create/edit worksheets.</li>
                            <li><strong>Student</strong> — can solve tasks in assigned worksheets; cannot generate.</li>
                            <li><strong>Admin</strong> — all teacher permissions plus user management and admin tools.</li>
                        </ul>

                        <h5>📝 Registration &amp; Login</h5>
                        <ul>
                            <li><strong>Register</strong> — teachers self-register with name, email, and password. Email verification is sent automatically.</li>
                            <li><strong>Login</strong> — email + password (teachers/admins), or a one-time token link (students).</li>
                            <li><strong>Token Login</strong> — students paste or click the link generated by their teacher. No email required to start.</li>
                        </ul>

                        <h5>🎓 Student Onboarding ("Anton-Prinzip")</h5>
                        <p>
                            Students do <em>not</em> self-register. The teacher creates student accounts inside a class.
                            CodeOwl generates a personal token link per student — share this link (printed, chat, etc.).
                            The student clicks it, is logged in immediately, and can optionally set an email and password
                            during onboarding. This minimises friction on day one.
                        </p>

                        <h5>🔑 Password Management</h5>
                        <ul>
                            <li><strong>Forgot Password</strong> — enter your email to receive a reset link.</li>
                            <li><strong>Reset Password</strong> — follow the link in the email to set a new password.</li>
                            <li><strong>Profile</strong> — change email address or password at any time via your profile page.</li>
                        </ul>

                        <h5>🔒 Security Notes</h5>
                        <ul>
                            <li>Passwords are hashed with Argon2 — they are never stored in plain text.</li>
                            <li>JWTs expire and auto-refresh in the background.</li>
                            <li>Student token links are single-use; once redeemed they cannot be reused.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "home":
                return (
                    <>
                        <h2>🏠 Home Page Help</h2>
                        <p>
                            The Home Page is your workspace for generating new tiered programming tasks.
                            CodeOwl accepts multiple types of input and creates three tasks (T1–T3) —
                            or five tasks (T1–T5) in extended mode.
                        </p>

                        <h5>💡 Supported Inputs</h5>
                        <ul>
                            <li><strong>Code Snippet</strong> — paste a valid snippet; the model creates statements and variants.</li>
                            <li><strong>Problem Statement</strong> — describe the problem in plain language.</li>
                            <li><strong>Concepts</strong> — list programming ideas like loops, arrays, or recursion.</li>
                            <li>
                                <strong>Important!</strong> — Either a Code Snippet must be present <em>or</em> the language
                                must be explicitly stated. Context can be derived from everything except the language alone.
                            </li>
                        </ul>

                        <h5>🎛️ Generation Options</h5>
                        <ul>
                            <li>
                                <strong>Target Language</strong> — forces problem statements and code comments to be in German
                                or English. Fixes the v1 problem where generation silently switched languages.
                            </li>
                            <li>
                                <strong>Tier Count</strong> — 3 tiers (default) or 5 tiers. Tier 4 adds distractor-heavy
                                puzzles; Tier 5 adds an "Explain Your Solution" component.
                            </li>
                            <li>
                                <strong>Distractors</strong> — enable or disable LLM-generated distractor blocks. When enabled,
                                a separate "Antagonist" call generates plausible-but-wrong lines per task.
                            </li>
                            <li>
                                <strong>Context Mode</strong>{" "}
                                <span className="badge bg-warning text-dark" style={{ fontSize: "0.7rem" }}>Beta</span>
                                {" "}— wraps the puzzle in read-only scaffolding code. The generated task receives
                                a <em>contextBefore</em> and <em>contextAfter</em> block (e.g. a class header above and
                                a calling function below). Students see the full picture but only rearrange the
                                puzzle blocks in the middle. Useful for longer, realistic code problems.
                            </li>
                        </ul>

                        <h5>⚙️ Advanced Options (Constraints)</h5>
                        <ul>
                            <li>
                                <strong>Blacklist</strong> — comma-separated constructs to exclude, e.g.,
                                <code>ternary, lambda, list comprehension</code>. Prevents LLM from using concepts
                                students haven't learned yet.
                            </li>
                            <li>
                                <strong>Whitelist</strong> — only use listed constructs. Useful for highly controlled exercises.
                            </li>
                            <li>
                                <strong>Difficulty Slope</strong> — controls how steep the complexity jump between tiers is
                                (gentle → steep).
                            </li>
                            <li>
                                <strong>Additional Context</strong> — freetext injected directly into the prompt, e.g.,
                                "Use only German variable names" or "Suitable for Jgst. 9".
                            </li>
                        </ul>

                        <h5>🧰 Controls</h5>
                        <ul>
                            <li><strong>Generate Sequence</strong> — sends inputs to the model and redirects to the sequence page.</li>
                            <li><strong>Demo</strong> — instantly loads a sample tiered sequence for testing.</li>
                            <li><strong>Reset</strong> — clears all input fields.</li>
                            <li><kbd>Ctrl</kbd>+<kbd>Enter</kbd> — keyboard shortcut to generate.</li>
                        </ul>

                        <h5>👩‍🏫 Teacher Tips</h5>
                        <ul>
                            <li>Very short snippets can cause wrong language detection — set the language explicitly.</li>
                            <li>Use clear, short statements. Avoid specifying variable names or function identifiers.</li>
                            <li>English input yields slightly better quality than German, but German works reliably with <em>Target Language: German</em> set.</li>
                            <li>If the generated tasks use constructs students don't know yet, add them to the Blacklist and regenerate.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "classes":
                return (
                    <>
                        <h2>🏫 Classes Help</h2>
                        <p>
                            Classes group students together and connect them to worksheets. Every student account
                            belongs to at least one class.
                        </p>

                        <h5>📋 Class Overview</h5>
                        <ul>
                            <li>The Classes page lists all your classes with student counts and assigned worksheets.</li>
                            <li>Click a class to open the detail view: student list + worksheet list.</li>
                        </ul>

                        <h5>➕ Creating Students ("Anton-Prinzip")</h5>
                        <p>
                            Click <strong>"Add Student"</strong> inside a class — enter a display name (first name,
                            e.g., "Anton"). CodeOwl creates the account and shows a one-time login token link.
                            Share this link with the student (print, chat, QR code). The student clicks it,
                            is instantly logged in, and can start solving tasks. No email address is required.
                        </p>
                        <ul>
                            <li>Token links can be regenerated if lost.</li>
                            <li>Students can later add an email and set a password via their profile page.</li>
                            <li>Students can be removed from a class at any time (this does <em>not</em> delete the account).</li>
                        </ul>

                        <h5>📝 Assigning Worksheets</h5>
                        <ul>
                            <li>From the class detail page, click <strong>"Assign Worksheet"</strong> and pick from your worksheets.</li>
                            <li>Assigned worksheets become accessible to all students in the class.</li>
                            <li>Worksheets can also be assigned directly from the Worksheet page.</li>
                        </ul>

                        <h5>📊 Tracking</h5>
                        <p>
                            Each class has a <strong>Tracking</strong> toggle (⚙ button in the class detail view).
                            When enabled, detailed learning events are recorded for every student interaction
                            in this class — block moves, feedback clicks, hint usage, completion times.
                        </p>
                        <ul>
                            <li><strong>Enabled</strong> — full event stream logged; suitable for research or detailed progress analysis.</li>
                            <li><strong>Disabled</strong> — only aggregate data is kept (correct/incorrect per task). Default for new classes.</li>
                            <li>Tracking state can be toggled at any time; it affects future events only.</li>
                        </ul>

                        <h5>🔑 Permissions</h5>
                        <ul>
                            <li>Only the class owner (or admin) can add/remove students or edit the class.</li>
                            <li>Other teachers can see a class if they share a worksheet with it, but cannot edit it.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "worksheets":
                return (
                    <>
                        <h2>📝 Worksheets Help</h2>
                        <p>
                            A worksheet is a named collection of sequences (e.g., "Week 3 — Arrays &amp; Loops").
                            Worksheets are the primary unit assigned to classes.
                        </p>

                        <h5>📋 Worksheet Overview</h5>
                        <ul>
                            <li>Create a worksheet from the Worksheets page or directly from a generated sequence.</li>
                            <li>Add sequences to a worksheet via the sequence page or the Library (one-click copy).</li>
                            <li>Sequences within a worksheet preserve their T1–T3 tier structure.</li>
                        </ul>

                        <h5>🔧 Hint Configuration (Teacher only)</h5>
                        <p>
                            Per worksheet, configure whether hints are available to students and how many credits
                            each student gets per task:
                        </p>
                        <ul>
                            <li><strong>Enable Hints</strong> — checkbox on/off. When off, the Hint button doesn't appear.</li>
                            <li><strong>Credits per Task</strong> — number (1–10, default 5). Level-1 hints cost 1 credit; Level-2 cost 2.</li>
                        </ul>

                        <h5>📤 Export</h5>
                        <ul>
                            <li><strong>Puzzle PDF</strong> — printable cut-and-arrange cards with dashed borders (for paper-based sessions).</li>
                            <li><strong>Free-Form PDF</strong> — problem statement + code in a grey monospace box (for written exams).</li>
                            <li><strong>JSON / CSV</strong> — raw task data for your own analysis (SPSS, R, Excel).</li>
                        </ul>

                        <h5>📋 Copying Foreign Worksheets</h5>
                        <ul>
                            <li>You can view any worksheet shared to you; click <strong>"Copy Worksheet"</strong> to get your own editable version.</li>
                            <li>The deep copy duplicates all sequences and tasks with new IDs — you are the sole owner.</li>
                            <li>Changes to the copy never affect the original.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "library":
                return (
                    <>
                        <h2>📚 Library Help</h2>
                        <p>
                            The Library is a searchable repository of all sequences available to you — your own,
                            plus those shared by colleagues. Find what you need, preview it, and copy it into a
                            worksheet with one click.
                        </p>

                        <h5>🔍 Searching</h5>
                        <ul>
                            <li>
                                <strong>Full-Text Search</strong> — searches problem statements and code simultaneously.
                                Postgres full-text indexing ensures fast, relevant results.
                            </li>
                            <li>
                                <strong>Highlighted Snippets</strong> — matching text is highlighted in the result card
                                so you immediately see why a sequence matched.
                            </li>
                            <li>
                                <strong>"Did you mean?"</strong> — trigram-based fuzzy suggestions for typos
                                (<em>recursiv</em> → <em>recursive</em>).
                            </li>
                        </ul>

                        <h5>🏷️ Tags &amp; Filters</h5>
                        <p>
                            Every sequence has two layers of tags:
                        </p>
                        <ul>
                            <li>
                                <strong>Structural Tags</strong> (grey) — automatically detected from code:
                                <code>for-loop</code>, <code>recursion</code>, <code>arrays</code>.
                                These are added synchronously right after generation.
                            </li>
                            <li>
                                <strong>Semantic Tags</strong> (with ✨) — LLM-generated topic labels:
                                <code>fibonacci</code>, <code>binary-search</code>, <code>caesar-cipher</code>.
                                These arrive a few seconds after generation.
                            </li>
                            <li>
                                <strong>Manual Tags</strong> (blue) — added by the teacher; never overwritten by auto-tagging.
                            </li>
                        </ul>
                        <p>Filters include: programming language, tier count (3 or 5), tags, date created, owner ("Mine only" / "All").</p>
                        <p>Filters are reflected in the URL so links can be shared.</p>

                        <h5>📋 One-Click Copy</h5>
                        <ul>
                            <li>Click <strong>"Copy to Worksheet"</strong> on any sequence card and select the target worksheet from the dropdown.</li>
                            <li>A toast notification confirms the copy with an Undo option (5 seconds).</li>
                            <li>Sequences already in one of your worksheets show a green ✓ indicator.</li>
                        </ul>

                        <h5>✏️ Editing Tags (Teacher)</h5>
                        <ul>
                            <li>Hover over a tag on your own sequence to reveal the remove (×) button.</li>
                            <li>Click the pencil icon to add new tags. An autocomplete suggests existing tags to avoid duplicates.</li>
                            <li>Manual tags are always blue and are never overwritten by the auto-tagger.</li>
                        </ul>

                        <h5>🔗 Library + Curriculum</h5>
                        <p>
                            From the Curriculum page, "View existing sequences" redirects to the Library with the
                            curriculum topic pre-filled as a filter — so you instantly see all relevant material
                            for a given LehrplanPLUS topic.
                        </p>
                    </>
                );

            // ------------------------------------------------------------------
            case "curriculum":
                return (
                    <>
                        <h2>🎓 Curriculum Help</h2>
                        <p>
                            The Curriculum page maps CodeOwl directly onto the Bavarian LehrplanPLUS G9 Informatik.
                            Browse by grade and learning area, then generate a sequence whose difficulty and
                            concepts are automatically calibrated to that topic — no manual setup needed.
                        </p>
                        <div className="alert alert-info d-inline-block py-1 px-3 mb-3" style={{ fontSize: "0.875rem" }}>
                            <strong>Beta feature.</strong> Curriculum-generated sequences use a dedicated prompt tuned
                            for the Bavarian curriculum. The underlying code examples are curated by hand.
                        </div>

                        <h5>🌳 Topic Tree</h5>
                        <ul>
                            <li>Topics are organised by grade (Jgst. 7, 9, 10, 11, 12/13) and learning area.</li>
                            <li>Each topic badge shows how many sequences already exist for that topic.</li>
                        </ul>

                        <h5>🚀 Generating from a Topic</h5>
                        <p>Click <strong>"Generate"</strong> next to a topic. The generation dialog opens pre-filled:</p>
                        <ul>
                            <li><strong>Concepts</strong> — automatically set from the curriculum topic mapping.</li>
                            <li><strong>Code Example</strong> — a curated reference code snippet for the topic is injected as seed material.</li>
                            <li><strong>Language</strong> — Java by default (as per Bavarian curriculum); can be changed.</li>
                            <li><strong>Grade Calibration</strong> — the system prompt internally adjusts expected difficulty and prerequisite knowledge.</li>
                        </ul>
                        <p>
                            You can modify any pre-filled value before generating, and all standard generation
                            options (constraints, tier count, target language) are available.
                        </p>

                        <h5>🔍 Viewing Existing Sequences</h5>
                        <p>
                            Click <strong>"View sequences"</strong> next to a topic to jump to the Library
                            pre-filtered for that topic. This includes both curriculum-generated and manually
                            tagged sequences that match.
                        </p>

                        <h5>👩‍🏫 Tips</h5>
                        <ul>
                            <li>For Jgst. 7 topics, keep constraints simple — the calibration already limits complexity.</li>
                            <li>Add your own curated code snippet instead of the default if you have a specific classroom example to use.</li>
                            <li>After generating, review the output in Teacher Mode before assigning to students.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "sequence":
                return (
                    <>
                        <h2>📋 Sequence Page Help</h2>
                        <p>
                            The Sequence Page displays all tasks within a generated sequence.
                            Each task corresponds to a tier — Tier 1 is easiest, Tier 3 (or 5) is most complex.
                        </p>

                        <h5>🔍 Overview</h5>
                        <ul>
                            <li><strong>Language / Concepts</strong> — detected programming language, concept tags, semantic summary.</li>
                            <li><strong>Task Description</strong> — the general topic for all tasks in the sequence.</li>
                            <li><strong>Code Snippet</strong> — original or reference code (if provided during generation).</li>
                            <li><strong>Distractor Indicator</strong> — a badge shows if distractor blocks are active for a task.</li>
                        </ul>

                        <h5>🎯 Actions</h5>
                        <ul>
                            <li><strong>🧩 Solve Task</strong> — opens the student solver view (<code>/solve/:id</code>).</li>
                            <li><strong>👩‍🏫 Review Task</strong> — opens the solver with teacher tools visible.</li>
                            <li><strong>Share Sequence</strong> — copies a student-safe share link to clipboard.</li>
                            <li><strong>✏️ Edit Task</strong> — modify the problem statement or code (teacher/owner only).</li>
                            <li><strong>📤 Export</strong> — download this sequence as PDF (puzzle or free-form) or JSON/CSV.</li>
                            <li><strong>Add to Worksheet</strong> — copy directly into one of your worksheets.</li>
                        </ul>

                        <h5>👩‍🏫 Teacher Mode</h5>
                        <ul>
                            <li>Automatically available for teachers and admins.</li>
                            <li>Expands solution accordions under each task card.</li>
                            <li>Shows distractor blocks alongside the model solution.</li>
                            <li>Allows switching to the teacher solver view with model solution overlay.</li>
                        </ul>

                        <h5>🧑‍🎓 Student Mode</h5>
                        <ul>
                            <li>Only problem statements are shown — no model solutions.</li>
                            <li>Students click "Solve Task" to open the interactive solver.</li>
                        </ul>

                        <h5>✏️ Task Editing &amp; Versioning</h5>
                        <p>
                            If you own a sequence (or have copied it), you can edit any task's problem statement
                            and code. The <strong>first save</strong> automatically preserves the original
                            LLM-generated version for comparison and research purposes. You will be asked to
                            rate the LLM output quality (1–5 stars) at first save.
                        </p>
                        <ul>
                            <li>On foreign sequences, the Edit button is locked — copy the worksheet first.</li>
                            <li>Original versions can be viewed via "Show Original" (teacher view).</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "solve":
                return (
                    <>
                        <h2>🧩 Solve Page Help</h2>
                        <p>
                            The Solve Page is where learners engage with Parsons problems — rearranging
                            shuffled code blocks (and identifying distractor blocks) to reconstruct
                            the correct program.
                        </p>

                        <h5>⚙️ How It Works</h5>
                        <ul>
                            <li>Blocks start shuffled in the "source" area. Move them to the "solution" area in the correct order.</li>
                            <li>Indent lines to create nested structures (drag-and-drop the indent handle, or use the indent buttons).</li>
                            <li>Press <strong>Feedback</strong> to check correctness at any time.</li>
                            <li>Use <strong>Shuffle Lines</strong> to randomise again if you want a fresh start.</li>
                        </ul>

                        <h5>🖱️ Interaction Modes</h5>
                        <ul>
                            <li>
                                <strong>Drag &amp; Drop</strong> (default) — grab a block and drop it into position.
                            </li>
                            <li>
                                <strong>Click-to-Move</strong> — click a block to select it (it turns highlighted),
                                then click the target slot to place it. Better for trackpads and tablets.
                                Toggle in the toolbar; preference is saved across sessions.
                            </li>
                            <li>
                                <strong>Keyboard</strong> — Tab to move between blocks, arrow keys to reorder selected block.
                                To indent a block via keyboard: select it, then use the <kbd>+</kbd> / <kbd>−</kbd> indent
                                buttons in the block toolbar, or drag the indent handle on the left edge of the block.
                            </li>
                            <li>
                                <strong>Touch</strong> — long-press to select a block on mobile, tap to place it.
                            </li>
                        </ul>

                        <h5>🎨 Feedback Colors</h5>
                        <ul>
                            <li><span style={{ color: "green" }}>🟩 Green</span> — correct line and indentation.</li>
                            <li><span style={{ color: "orange" }}>🟨 Yellow</span> — indentation warning (non-critical, often still accepted).</li>
                            <li><span style={{ color: "red" }}>🟥 Red</span> — incorrect position or indentation.</li>
                        </ul>

                        <h5>🎭 Distractor Blocks</h5>
                        <p>
                            Some puzzles include distractor blocks — plausible-looking lines that do <em>not</em> belong
                            in the correct solution. Distractor blocks look identical to real blocks; part of the challenge
                            is identifying them and leaving them in the source area.
                        </p>
                        <ul>
                            <li>A distractor in your solution zone will always be marked red on Feedback.</li>
                            <li>Hints can help identify distractor blocks (they show up as a distinct hint type).</li>
                        </ul>

                        <h5>🔀 Interchangeable Blocks</h5>
                        <p>
                            Some tasks contain blocks that are <em>equally valid in multiple positions</em> — for example,
                            two independent variable declarations, or two independent base cases in a recursive function.
                            The grader recognises all valid permutations and marks them green.
                        </p>

                        <h5>
                            📐 Context Code{" "}
                            <span className="badge bg-warning text-dark" style={{ fontSize: "0.7rem" }}>Beta</span>
                        </h5>
                        <p>
                            Some tasks show a <strong>grey read-only block</strong> above and/or below the puzzle area.
                            This is <em>context code</em> — the surrounding scaffold (e.g. a class definition,
                            an import block, or a function that calls your result). You do <strong>not</strong> move
                            these blocks; they are fixed. They exist to give you the full picture of where your
                            solution fits. The grader ignores them — only the movable puzzle blocks are checked.
                        </p>

                        <h5>💡 Hints</h5>
                        <p>
                            When stuck, click the <strong>Hint 💡</strong> button (only visible if hints are enabled for this worksheet):
                        </p>
                        <ul>
                            <li>
                                <strong>Level 1 (1 credit)</strong> — a static analysis tool (Ruff for Python, javac + SpotBugs for Java)
                                analyses your current arrangement and highlights the problematic block(s) with a red pulsing border.
                                A short message explains the type of error (e.g., "A block uses a variable that hasn't been defined yet").
                            </li>
                            <li>
                                <strong>Level 2 (2 credits)</strong> — click "Explain in detail" to get an LLM-generated explanation
                                that references the highlighted blocks by label (A, B, …) and explains the logic in student-friendly language.
                            </li>
                            <li>Hints disappear after you move a block — encouraging you to act, not just read.</li>
                            <li>Remaining credits are shown next to the button: 💡 3/5.</li>
                        </ul>

                        <h5>✅ Completion</h5>
                        <p>
                            When you solve the puzzle correctly:
                        </p>
                        <ul>
                            <li>A <strong>"✓ Solved!"</strong> banner appears with your stats: attempts, time, hints used.</li>
                            <li>A short self-report (confidence rating) may appear — this is anonymous research data.</li>
                            <li>Use the <strong>"Next Task"</strong> button to advance to the next tier.</li>
                        </ul>

                        <h5>🧭 Navigation</h5>
                        <ul>
                            <li><strong>← Back to Sequence</strong> — returns to the sequence overview.</li>
                            <li><strong>🏠 Home</strong> — back to the generator.</li>
                            <li><strong>🔗 Share Task</strong> — copies a student-safe task link to clipboard.</li>
                        </ul>

                        {isTeacherMode ? (
                            <>
                                <h5>👩‍🏫 Teacher Tools</h5>
                                <ul>
                                    <li>Toggle Teacher Mode at the top right to show/hide the model solution panel.</li>
                                    <li>The "Teacher Tools" box (bottom right) shows the model solution and distractor list.</li>
                                    <li>Share button copies a student-facing version (without teacher mode enabled).</li>
                                    <li>Block labels (A, B, …) in the model solution correspond to hint labels — useful for classroom discussion.</li>
                                </ul>
                            </>
                        ) : (
                            <>
                                <h5>🧑‍🎓 Student Tips</h5>
                                <ul>
                                    <li>Focus on logic, not syntax perfection — the grader understands minor indentation variations.</li>
                                    <li>Use feedback iteratively; don't guess wildly. Try to understand each red block before moving on.</li>
                                    <li>If a block keeps coming up red no matter where you put it, consider whether it might be a distractor.</li>
                                    <li>Try Click-to-Move if drag-and-drop feels awkward on your device.</li>
                                    <li>Use hints strategically — you have a limited credit budget per task.</li>
                                </ul>
                            </>
                        )}
                    </>
                );

            // ------------------------------------------------------------------
            case "hints":
                return (
                    <>
                        <h2>💡 Hint System Help</h2>
                        <p>
                            The hint system provides structured, reliable help when a student is stuck.
                            Unlike purely LLM-based hints, CodeOwl's hints are <strong>deterministic first</strong>:
                            real static analysis tools run on the student's current block arrangement to identify
                            concrete problems.
                        </p>
                        <div className="alert alert-secondary d-inline-block py-1 px-3 mb-3" style={{ fontSize: "0.875rem" }}>
                            Design principle: <em>"Möglichst deterministisch — kein LLM-Halluzinationsrisiko."</em>
                        </div>

                        <h5>🔁 How It Works</h5>
                        <ol>
                            <li>The student's current block arrangement is assembled into a complete code file.</li>
                            <li>A static analysis tool runs on that file.</li>
                            <li>Findings are mapped back to specific block IDs.</li>
                            <li>The affected blocks are highlighted with a red pulsing border.</li>
                            <li>A short pedagogical message describes the type of error — without giving the answer.</li>
                        </ol>

                        <h5>🛠️ Analysis Tools</h5>
                        <ul>
                            <li>
                                <strong>Python → Ruff</strong> — ultra-fast Python linter.
                                Key rules: <code>F821</code> (undefined name — block used before definition),
                                <code>F841</code> (unused variable — block may be a distractor),
                                <code>E999</code> (syntax error), <code>F811</code> (redefined name).
                            </li>
                            <li>
                                <strong>Java → javac (Stage 1)</strong> — compiler diagnostics catch undefined variables,
                                type errors, and uninitialized variables.
                            </li>
                            <li>
                                <strong>Java → SpotBugs (Stage 2)</strong> — bytecode analysis for deeper bugs
                                (null-pointer paths, dead stores, redundant null-checks). Only runs if the code compiles.
                            </li>
                        </ul>

                        <h5>📊 Hint Levels</h5>
                        <table className="table table-bordered table-sm mt-2">
                            <thead className="table-light">
                                <tr>
                                    <th>Level</th>
                                    <th>Name</th>
                                    <th>Source</th>
                                    <th>Credits</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td>0</td>
                                    <td>Standard Feedback</td>
                                    <td>js-parsons (position + indent check)</td>
                                    <td>0 (free)</td>
                                </tr>
                                <tr>
                                    <td>1</td>
                                    <td>Linter Hint</td>
                                    <td>Ruff / javac / SpotBugs → deterministic</td>
                                    <td>1</td>
                                </tr>
                                <tr>
                                    <td>2</td>
                                    <td>"Explain in detail"</td>
                                    <td>LLM explains the finding in student-friendly language</td>
                                    <td>2</td>
                                </tr>
                            </tbody>
                        </table>
                        <p className="text-muted" style={{ fontSize: "0.875rem" }}>
                            Level 2 is not a diagnosis — the LLM receives the already-identified finding and only
                            explains it. It does not analyse the code independently.
                        </p>

                        <h5>🎭 Distractor-Aware Hints</h5>
                        <p>
                            If a distractor block is in the student's solution zone, the hint system detects this
                            as the priority problem before running the full analysis:
                        </p>
                        <ul>
                            <li>Hint text: "One of the blocks in your workspace doesn't belong in the solution."</li>
                            <li>The distractor block is highlighted — but not explicitly named as a distractor.</li>
                        </ul>

                        <h5>⚠️ Edge Cases</h5>
                        <ul>
                            <li>
                                <strong>Too few blocks placed</strong> — if you have fewer blocks in the solution area than
                                the correct answer requires, the hint tells you immediately: "You have placed X of Y needed
                                blocks." This takes priority over all other findings so you don't get misleading analysis
                                on an incomplete arrangement.
                            </li>
                            <li>
                                <strong>No findings</strong> — if the arrangement compiles/lints without errors but is still wrong,
                                the fallback message is: "No errors found, but the order isn't right yet. Think about what needs
                                to happen first."
                            </li>
                            <li>
                                <strong>Multiple findings</strong> — only the top 2 most severe findings are shown to avoid overwhelming the student.
                            </li>
                            <li>
                                <strong>Tool timeout / crash</strong> — graceful degradation: positional fallback or "Hints temporarily unavailable".
                            </li>
                        </ul>

                        <h5>👩‍🏫 Configuring Hints (Teacher)</h5>
                        <p>From the Worksheet page (Teacher view):</p>
                        <ul>
                            <li><strong>Enable Hints</strong> — checkbox. Off = Hint button hidden entirely.</li>
                            <li><strong>Credits per Task</strong> — 1–10 (default 5). Encourages strategic use over dependency.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "distractors":
                return (
                    <>
                        <h2>🎭 Distractor System Help</h2>
                        <p>
                            Distractor blocks are plausible-looking but incorrect code lines that are mixed into
                            the puzzle block pool. The student must identify them and leave them in the source area.
                            They test whether students genuinely understand the code — not just rearrangement.
                        </p>

                        <h5>🏷️ Distractor Taxonomy</h5>
                        <table className="table table-bordered table-sm">
                            <thead className="table-light">
                                <tr>
                                    <th>Type</th>
                                    <th>Description</th>
                                    <th>Tests</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td><code>LOGIC_FLIP</code></td>
                                    <td>Operator inverted (<code>&lt;</code> → <code>&gt;</code>, <code>==</code> → <code>!=</code>)</td>
                                    <td>Conditional understanding</td>
                                </tr>
                                <tr>
                                    <td><code>OFF_BY_ONE</code></td>
                                    <td>Wrong loop bounds (<code>range(n)</code> vs <code>range(n+1)</code>)</td>
                                    <td>Boundary values</td>
                                </tr>
                                <tr>
                                    <td><code>VARIABLE_MISMATCH</code></td>
                                    <td>Correct style, wrong variable used in expression</td>
                                    <td>Data flow analysis</td>
                                </tr>
                                <tr>
                                    <td><code>BOUNDARY_IDENTITY</code></td>
                                    <td>Wrong initial value or base case</td>
                                    <td>Recursion / accumulator thinking</td>
                                </tr>
                                <tr>
                                    <td><code>STRUCTURAL_ILLOGICAL</code></td>
                                    <td>Syntactically valid but contextually illegal (<code>break</code> outside loop)</td>
                                    <td>Control flow rules</td>
                                </tr>
                                <tr>
                                    <td><code>REDUNDANT_ZOMBIE</code></td>
                                    <td>Plausible but unnecessary extra line</td>
                                    <td>Identifying superfluous code</td>
                                </tr>
                            </tbody>
                        </table>

                        <h5>🤖 How Distractors Are Generated</h5>
                        <ol>
                            <li><strong>Plan</strong> — during sequence generation, the model chooses a distractor type and rationale per task (e.g., "LOGIC_FLIP: focus on Boolean inversion for early exits").</li>
                            <li><strong>Solver pass</strong> — the correct solution is generated as normal.</li>
                            <li><strong>Antagonist call</strong> — a separate LLM call ("CodeOwl Antagonist") generates the actual distractor lines, guided by the plan. Distractors end with <code>#distractor</code> internally.</li>
                        </ol>
                        <p>The solver and the antagonist are separate — the antagonist only sees the correct solution and the plan, not the solver's internal reasoning.</p>

                        <h5>👩‍🏫 Teacher Controls</h5>
                        <ul>
                            <li><strong>Enable/Disable</strong> — toggle distractors per task or per sequence during/after generation.</li>
                            <li><strong>Edit Distractors</strong> — open the Task Edit Modal → "Distractors" tab → edit, add, or remove distractor lines.</li>
                            <li><strong>Regenerate</strong> — click "Regenerate Distractors" to get a fresh set from the antagonist.</li>
                            <li>The distractor plan (type + rationale) is shown for context so you know what the AI was trying to achieve.</li>
                        </ul>

                        <h5>🧑‍🎓 Student Tips</h5>
                        <ul>
                            <li>If a block stays red no matter where you place it, it may be a distractor.</li>
                            <li>Read each block carefully — distractors are designed to look plausible at a glance.</li>
                            <li>Hints can help: if a distractor is in your solution zone, the hint system specifically flags it.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "teacher":
                return (
                    <>
                        <h2>👩‍🏫 Teacher Mode Help</h2>
                        <p>
                            Teachers and admins automatically have access to Teacher Mode across all pages.
                            Teacher Mode reveals model solutions, enables editing, and surfaces research tools.
                        </p>

                        <h5>🔄 Toggling Teacher Mode</h5>
                        <ul>
                            <li>Look for the yellow <strong>"Lehrer-Ansicht"</strong> button in the top-right of sequence and solve pages.</li>
                            <li>Students never see this button — teacher controls are gated by role, not just by URL.</li>
                        </ul>

                        <h5>📋 Sequence Page (Teacher)</h5>
                        <ul>
                            <li>Model solutions expand in accordion panels under each task card.</li>
                            <li>Distractor blocks are listed alongside the solution.</li>
                            <li>Edit, export, and copy buttons are visible.</li>
                            <li>Rating badge shows the LLM quality rating given when the task was first edited.</li>
                        </ul>

                        <h5>🧩 Solve Page (Teacher)</h5>
                        <ul>
                            <li>"Teacher Tools" panel (bottom right) shows the model solution with block labels.</li>
                            <li>Share button generates a student-safe link (without teacher parameters).</li>
                            <li>Full hint functionality is available for testing even without a credit budget.</li>
                        </ul>

                        <h5>✏️ Task Editing &amp; Versioning</h5>
                        <ul>
                            <li>Edit any task you own via the "Edit" button on the Sequence page.</li>
                            <li>The <strong>first save</strong> preserves the LLM original in the database for research comparison.</li>
                            <li>You rate the original LLM quality (1–5 stars) at first save — this becomes part of the research dataset.</li>
                            <li>All edits and their process (started / saved / cancelled) are logged as learning events for research.</li>
                        </ul>

                        <h5>📊 What Data Is Collected?</h5>
                        <p>
                            CodeOwl v2 logs structured learning events for research purposes (event-based, JSONB).
                            This includes:
                        </p>
                        <ul>
                            <li>Task open/close times, feedback clicks, correct/abandoned events</li>
                            <li>Every individual block move (for strategy analysis)</li>
                            <li>Hint usage (level, timing, which blocks were highlighted)</li>
                            <li>Distractor interactions (placed, removed, time in workspace)</li>
                            <li>Teacher edit events (started, saved, duration, fields changed)</li>
                            <li>Self-report ratings (confidence, frustration — 1-click smileys after completion)</li>
                            <li>Session metadata (device type, viewport — once per session)</li>
                        </ul>
                        <p className="text-muted" style={{ fontSize: "0.875rem" }}>
                            No raw mouse tracking, no keystroke logging, no external tab tracking.
                            Anonymous use (no login) is always possible — those events have <code>user_id = null</code>.
                        </p>

                        {isAdmin && (
                            <>
                                <h5>🔒 Admin Features</h5>
                                <ul>
                                    <li><strong>User Management</strong> — view, edit, promote, and delete users.</li>
                                    <li><strong>Bulk Retag</strong> — re-run tag analysis across all sequences (useful after model updates or tag config changes).</li>
                                    <li><strong>Library Admin</strong> — merge duplicate tags, view newly discovered topics.</li>
                                </ul>
                                <p>
                                    <Link to="/help/admin">→ Full Admin documentation</Link>
                                </p>
                            </>
                        )}
                    </>
                );

            // ------------------------------------------------------------------
            case "manual-create":
                return (
                    <>
                        <h2>✏️ Manual Task Creator Help</h2>
                        <p>
                            The Manual Task Creator lets you build a Parsons puzzle sequence without any LLM
                            involvement — you write the problem statement and the correct code solution for each
                            tier yourself. Use this when you want full control, or when you already have
                            classroom code examples you want to turn into puzzles.
                        </p>

                        <h5>📋 Workflow</h5>
                        <ol>
                            <li>Set the <strong>programming language</strong> and <strong>concepts</strong> (comma-separated, e.g. <code>for-loop, arrays</code>).</li>
                            <li>For each tier (T1–T3), write a <strong>problem statement</strong> and paste the <strong>correct code solution</strong>.</li>
                            <li>Watch the <strong>live block preview</strong> on the right — it shows exactly how the code will be split into draggable blocks.</li>
                            <li>Click <strong>"Create Sequence"</strong> to save. You're redirected to the new sequence page.</li>
                        </ol>

                        <h5>🧩 How Code Becomes Blocks</h5>
                        <p>
                            Every <strong>non-empty line</strong> in the solution editor becomes one block in the Parsons
                            puzzle. Indentation in the code is automatically translated into indent levels — so a line
                            indented by 4 spaces becomes a block at indent level 1, 8 spaces → level 2, etc.
                        </p>
                        <ul>
                            <li>Keep solutions focused — 5 to 12 non-empty lines is the sweet spot.</li>
                            <li>The line count and character count are shown below the editor.</li>
                            <li><kbd>Tab</kbd> in the code editor inserts 4 spaces (no focus-trap).</li>
                        </ul>

                        <h5>🔢 Tiers</h5>
                        <ul>
                            <li>Start with 1–3 tiers. Each tier is a separate puzzle at a different difficulty level.</li>
                            <li>Use the tab buttons at the top to switch between tiers — green badge ✓ means that tier has both a statement and a solution.</li>
                            <li>Use the <strong>Tier Level</strong> selector to assign T1 (easy), T2 (medium), or T3 (hard) to each slot.</li>
                            <li>You can add or remove tiers using the + / × buttons.</li>
                        </ul>

                        <h5>🆚 Manual vs. Generate</h5>
                        <table className="table table-bordered table-sm mt-2">
                            <thead className="table-light">
                                <tr><th></th><th>Manual Creator</th><th>AI Generator</th></tr>
                            </thead>
                            <tbody>
                                <tr><td>LLM used?</td><td>No</td><td>Yes</td></tr>
                                <tr><td>Speed</td><td>As fast as you type</td><td>~10–30 seconds</td></tr>
                                <tr><td>Control</td><td>Full — every word</td><td>Partial — prompt + constraints</td></tr>
                                <tr><td>Distractors</td><td>Generate after creation</td><td>Can enable during generation</td></tr>
                                <tr><td>Best for</td><td>Existing classroom code, exams</td><td>Exploring new topics quickly</td></tr>
                            </tbody>
                        </table>

                        <h5>💡 Tips</h5>
                        <ul>
                            <li>After creation, you can still generate distractors via the sequence page.</li>
                            <li>The sequence is tagged automatically after creation — check the Library to verify tags.</li>
                            <li>Tiers don't have to share the same algorithm — T1 could be a simple loop, T3 a recursive version.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "my-classes":
                return (
                    <>
                        <h2>🎓 My Classes Help (Students)</h2>
                        <p>
                            The My Classes page is your home screen as a student. It shows all the classes your
                            teacher has enrolled you in, and from there you can access the worksheets assigned
                            to your class.
                        </p>

                        <h5>🗂️ Navigating to Tasks</h5>
                        <ol>
                            <li>Open <strong>My Classes</strong> from the navigation bar.</li>
                            <li>Click a class card to open the class detail page.</li>
                            <li>You'll see the worksheets your teacher has assigned — click one to open it.</li>
                            <li>The worksheet lists all task sequences. Click <strong>"Solve Task"</strong> to start a puzzle.</li>
                        </ol>

                        <h5>❓ Common Questions</h5>
                        <ul>
                            <li>
                                <strong>My class list is empty.</strong> — Ask your teacher to enrol you.
                                You need to be added to a class before worksheets appear here.
                            </li>
                            <li>
                                <strong>I don't see a worksheet my teacher mentioned.</strong> — The teacher may not
                                have assigned it to your class yet, or you may be in the wrong class.
                            </li>
                            <li>
                                <strong>Can I see tasks not assigned to my class?</strong> — Yes, if you have a
                                direct link to a sequence or worksheet you can still view and solve it anonymously.
                            </li>
                        </ul>

                        <h5>🔐 Your Account</h5>
                        <ul>
                            <li>Your teacher created your account and gave you a one-time login link.</li>
                            <li>After logging in, you can set a personal password and email via your <strong>Profile</strong>.</li>
                            <li>Your progress (solved tasks, hints used) is saved to your account.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            case "admin":
                if (!isAdmin) {
                    return <p>Diese Seite ist nur für Admins zugänglich.</p>;
                }
                return (
                    <>
                        <h2>🔒 Admin Panel Help</h2>
                        <p>
                            Admin tools give you global oversight of the CodeOwl platform — user accounts,
                            tag hygiene, and bulk library operations. These pages are not visible to teachers or students.
                        </p>

                        <h5>👥 User Management <code>/admin/users</code></h5>
                        <ul>
                            <li><strong>View all users</strong> — list of every registered account with role, email, and creation date.</li>
                            <li><strong>Edit role</strong> — promote a teacher to admin or demote back.</li>
                            <li><strong>Delete user</strong> — removes the account. Sequences and worksheets owned by that user are preserved (owner_id is kept, but the user cannot log in).</li>
                            <li><strong>Search / filter</strong> — filter by role or search by name/email.</li>
                        </ul>

                        <h5>🏷️ Library Admin <code>/admin/library</code></h5>
                        <ul>
                            <li>
                                <strong>Bulk Retag</strong> — re-runs the automatic tag analysis pipeline across all sequences
                                in the database. Use this after updating the tagger model or changing tag rules.
                                Can take a few minutes depending on library size.
                            </li>
                            <li>
                                <strong>Merge Tags</strong> — combine duplicate or near-duplicate tags into one
                                canonical form (e.g. <code>for_loop</code> + <code>for-loop</code> → <code>for-loop</code>).
                                All sequence associations are updated automatically.
                            </li>
                            <li>
                                <strong>Tag Overview</strong> — see all tags sorted by usage count; spot orphaned or
                                rarely-used tags that could be cleaned up.
                            </li>
                        </ul>

                        <h5>⚠️ Caution</h5>
                        <ul>
                            <li>Bulk Retag overwrites existing auto-generated tags. <strong>Manual tags are never touched.</strong></li>
                            <li>Merging tags is irreversible via the UI — make a DB backup before large merge operations.</li>
                            <li>Deleting users does not cascade-delete their content — verify you want to keep orphaned sequences.</li>
                        </ul>
                    </>
                );

            // ------------------------------------------------------------------
            default:
                return <p>No help content available for this section.</p>;
        }
    };

    return (
        <div className="container py-4" style={{ maxWidth: 840 }}>
            {renderContent()}
            <hr className="my-4" />
            <div className="d-flex flex-wrap gap-2">
                <button onClick={goBack} className="btn btn-outline-secondary">
                    ← Back
                </button>
                <Link to="/help" className="btn btn-link ms-auto">
                    View full help overview →
                </Link>
            </div>
        </div>
    );
}
