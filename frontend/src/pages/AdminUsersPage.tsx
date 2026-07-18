import { useEffect, useState, useCallback } from "react";
import { useTranslation } from "react-i18next";
import { api } from "../lib/api";

interface AdminUser {
    userId: string;
    email: string | null;
    firstName: string;
    lastName: string;
    role: string;
    verified: boolean;
    active: boolean;
    createdAt: string | null;
}

interface PageResponse {
    content: AdminUser[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
}

export default function AdminUsersPage() {
    const { t } = useTranslation();
    const [users, setUsers] = useState<AdminUser[]>([]);
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [sort, setSort] = useState("email");
    const [dir, setDir] = useState("asc");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");
    const [successMsg, setSuccessMsg] = useState("");
    const pageSize = 20;

    // Edit modal state
    const [editUser, setEditUser] = useState<AdminUser | null>(null);
    const [editFirstName, setEditFirstName] = useState("");
    const [editLastName, setEditLastName] = useState("");
    const [editEmail, setEditEmail] = useState("");
    const [editRole, setEditRole] = useState("");
    const [editSaving, setEditSaving] = useState(false);

    // Delete confirm state
    const [deleteUser, setDeleteUser] = useState<AdminUser | null>(null);
    const [deleting, setDeleting] = useState(false);

    const loadUsers = useCallback(async () => {
        setLoading(true);
        setError("");
        try {
            const res = await api.get<PageResponse>("/admin/users", {
                params: { search, page, size: pageSize, sort, dir },
            });
            setUsers(res.data.content);
            setTotalPages(res.data.totalPages);
            setTotalElements(res.data.totalElements);
        } catch (e: any) {
            setError(e.response?.data?.error || t("admin.errorLoading"));
        } finally {
            setLoading(false);
        }
    }, [search, page, sort, dir]);

    useEffect(() => {
        loadUsers();
    }, [loadUsers]);

    // Debounced search
    const [searchInput, setSearchInput] = useState("");
    useEffect(() => {
        const timer = setTimeout(() => {
            setSearch(searchInput);
            setPage(0);
        }, 300);
        return () => clearTimeout(timer);
    }, [searchInput]);

    const handleSort = (col: string) => {
        if (sort === col) {
            setDir(dir === "asc" ? "desc" : "asc");
        } else {
            setSort(col);
            setDir("asc");
        }
        setPage(0);
    };

    const sortIcon = (col: string) => {
        if (sort !== col) return <i className="bi bi-arrow-down-up text-muted ms-1" style={{ fontSize: "0.7rem" }}></i>;
        return dir === "asc"
            ? <i className="bi bi-sort-up ms-1"></i>
            : <i className="bi bi-sort-down ms-1"></i>;
    };

    const openEdit = (u: AdminUser) => {
        setEditUser(u);
        setEditFirstName(u.firstName);
        setEditLastName(u.lastName);
        setEditEmail(u.email || "");
        setEditRole(u.role);
    };

    const handleEditSave = async () => {
        if (!editUser) return;
        setEditSaving(true);
        try {
            await api.put(`/admin/users/${editUser.userId}`, {
                firstName: editFirstName,
                lastName: editLastName,
                email: editEmail,
                role: editRole,
            });
            setEditUser(null);
            setSuccessMsg(t("admin.userUpdated"));
            setTimeout(() => setSuccessMsg(""), 3000);
            loadUsers();
        } catch (e: any) {
            setError(e.response?.data?.error || t("admin.errorSaving"));
        } finally {
            setEditSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!deleteUser) return;
        setDeleting(true);
        try {
            await api.delete(`/admin/users/${deleteUser.userId}`);
            setDeleteUser(null);
            setSuccessMsg(t("admin.userDeleted"));
            setTimeout(() => setSuccessMsg(""), 3000);
            loadUsers();
        } catch (e: any) {
            setError(e.response?.data?.error || t("admin.errorDeleting"));
        } finally {
            setDeleting(false);
        }
    };

    const handleToggleAdmin = async (u: AdminUser) => {
        try {
            await api.post(`/admin/users/${u.userId}/make-admin`);
            setSuccessMsg(t("admin.roleChanged", { name: `${u.firstName} ${u.lastName}` }));
            setTimeout(() => setSuccessMsg(""), 3000);
            loadUsers();
        } catch (e: any) {
            setError(e.response?.data?.error || t("admin.errorChangingRole"));
        }
    };

    const handleResetPassword = async (u: AdminUser) => {
        try {
            await api.post(`/admin/users/${u.userId}/reset-password`);
            setSuccessMsg(t("admin.resetSent", { email: u.email }));
            setTimeout(() => setSuccessMsg(""), 3000);
        } catch (e: any) {
            setError(e.response?.data?.error || t("admin.errorSending"));
        }
    };

    const roleLabel = (role: string) => {
        const map: Record<string, { label: string; color: string }> = {
            ADMIN: { label: t("admin.roleAdmin"), color: "danger" },
            TEACHER: { label: t("admin.roleTeacher"), color: "primary" },
            STUDENT: { label: t("admin.roleStudent"), color: "success" },
        };
        const r = map[role] || { label: role, color: "secondary" };
        return <span className={`badge bg-${r.color}`}>{r.label}</span>;
    };

    const formatDate = (dateStr: string | null) => {
        if (!dateStr) return "-";
        try {
            return new Date(dateStr).toLocaleDateString("de-DE");
        } catch {
            return dateStr;
        }
    };

    return (
        <div className="container-fluid py-4" style={{ maxWidth: 1100 }}>
            <h2 className="mb-4">
                <i className="bi bi-shield-lock me-2"></i>
                {t("admin.title")}
            </h2>

            {error && (
                <div className="alert alert-danger alert-dismissible">
                    {error}
                    <button className="btn-close" onClick={() => setError("")}></button>
                </div>
            )}
            {successMsg && (
                <div className="alert alert-success alert-dismissible">
                    {successMsg}
                    <button className="btn-close" onClick={() => setSuccessMsg("")}></button>
                </div>
            )}

            {/* Search bar */}
            <div className="mb-3">
                <div className="input-group" style={{ maxWidth: 400 }}>
                    <span className="input-group-text"><i className="bi bi-search"></i></span>
                    <input
                        className="form-control"
                        placeholder={t("admin.searchPlaceholder")}
                        value={searchInput}
                        onChange={e => setSearchInput(e.target.value)}
                    />
                    {searchInput && (
                        <button className="btn btn-outline-secondary" onClick={() => setSearchInput("")}>
                            <i className="bi bi-x"></i>
                        </button>
                    )}
                </div>
                <small className="text-muted">{t("admin.totalUsers", { count: totalElements })}</small>
            </div>

            {/* Users table */}
            {loading ? (
                <div className="text-center py-5">
                    <div className="spinner-border" role="status">
                        <span className="visually-hidden">{t("admin.loading")}</span>
                    </div>
                </div>
            ) : (
                <>
                    <div className="table-responsive">
                        <table className="table table-hover align-middle">
                            <thead className="table-light">
                                <tr>
                                    <th style={{ cursor: "pointer" }} onClick={() => handleSort("firstName")}>
                                        {t("admin.nameHeader")} {sortIcon("firstName")}
                                    </th>
                                    <th style={{ cursor: "pointer" }} onClick={() => handleSort("email")}>
                                        {t("admin.emailHeader")} {sortIcon("email")}
                                    </th>
                                    <th style={{ cursor: "pointer" }} onClick={() => handleSort("role")}>
                                        {t("admin.roleHeader")} {sortIcon("role")}
                                    </th>
                                    <th style={{ cursor: "pointer" }} onClick={() => handleSort("verified")}>
                                        {t("admin.statusHeader")} {sortIcon("verified")}
                                    </th>
                                    <th style={{ cursor: "pointer" }} onClick={() => handleSort("createdAt")}>
                                        {t("admin.createdHeader")} {sortIcon("createdAt")}
                                    </th>
                                    <th>{t("admin.actionsHeader")}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {users.length === 0 ? (
                                    <tr>
                                        <td colSpan={6} className="text-center text-muted py-4">
                                            {t("admin.noUsers")}
                                        </td>
                                    </tr>
                                ) : (
                                    users.map(u => (
                                        <tr key={u.userId}>
                                            <td>{u.firstName} {u.lastName}</td>
                                            <td>{u.email || <span className="text-muted">-</span>}</td>
                                            <td>{roleLabel(u.role)}</td>
                                            <td>
                                                {u.verified
                                                    ? <span className="badge bg-success">{t("admin.verified")}</span>
                                                    : <span className="badge bg-warning text-dark">{t("admin.notVerified")}</span>
                                                }
                                            </td>
                                            <td>{formatDate(u.createdAt)}</td>
                                            <td>
                                                <div className="btn-group btn-group-sm">
                                                    <button className="btn btn-outline-primary" title={t("admin.editUser")} onClick={() => openEdit(u)}>
                                                        <i className="bi bi-pencil"></i>
                                                    </button>
                                                    <button
                                                        className={`btn btn-outline-${u.role === "ADMIN" ? "warning" : "info"}`}
                                                        title={u.role === "ADMIN" ? t("admin.removeAdmin") : t("admin.makeAdmin")}
                                                        onClick={() => handleToggleAdmin(u)}
                                                    >
                                                        <i className="bi bi-shield"></i>
                                                    </button>
                                                    <button className="btn btn-outline-secondary" title={t("admin.resetPassword")} onClick={() => handleResetPassword(u)}>
                                                        <i className="bi bi-key"></i>
                                                    </button>
                                                    <button className="btn btn-outline-danger" title={t("admin.deleteUser")} onClick={() => setDeleteUser(u)}>
                                                        <i className="bi bi-trash"></i>
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>

                    {/* Pagination */}
                    {totalPages > 1 && (
                        <nav>
                            <ul className="pagination justify-content-center">
                                <li className={`page-item ${page === 0 ? "disabled" : ""}`}>
                                    <button className="page-link" onClick={() => setPage(p => Math.max(0, p - 1))}>
                                        <i className="bi bi-chevron-left"></i>
                                    </button>
                                </li>
                                {Array.from({ length: totalPages }, (_, i) => i)
                                    .filter(i => Math.abs(i - page) <= 2 || i === 0 || i === totalPages - 1)
                                    .map((i, idx, arr) => {
                                        const elements = [];
                                        if (idx > 0 && arr[idx - 1] !== i - 1) {
                                            elements.push(
                                                <li key={`ellipsis-${i}`} className="page-item disabled">
                                                    <span className="page-link">...</span>
                                                </li>
                                            );
                                        }
                                        elements.push(
                                            <li key={i} className={`page-item ${i === page ? "active" : ""}`}>
                                                <button className="page-link" onClick={() => setPage(i)}>
                                                    {i + 1}
                                                </button>
                                            </li>
                                        );
                                        return elements;
                                    })
                                }
                                <li className={`page-item ${page >= totalPages - 1 ? "disabled" : ""}`}>
                                    <button className="page-link" onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}>
                                        <i className="bi bi-chevron-right"></i>
                                    </button>
                                </li>
                            </ul>
                        </nav>
                    )}
                </>
            )}

            {/* Edit Modal */}
            {editUser && (
                <div className="modal d-block" tabIndex={-1} style={{ backgroundColor: "rgba(0,0,0,0.5)" }}>
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">{t("admin.editTitle")}</h5>
                                <button className="btn-close" onClick={() => setEditUser(null)}></button>
                            </div>
                            <div className="modal-body">
                                <div className="mb-2">
                                    <label className="form-label">{t("admin.firstName")}</label>
                                    <input className="form-control" value={editFirstName} onChange={e => setEditFirstName(e.target.value)} />
                                </div>
                                <div className="mb-2">
                                    <label className="form-label">{t("admin.lastName")}</label>
                                    <input className="form-control" value={editLastName} onChange={e => setEditLastName(e.target.value)} />
                                </div>
                                <div className="mb-2">
                                    <label className="form-label">{t("admin.email")}</label>
                                    <input className="form-control" type="email" value={editEmail} onChange={e => setEditEmail(e.target.value)} />
                                </div>
                                <div className="mb-2">
                                    <label className="form-label">{t("admin.role")}</label>
                                    <select className="form-select" value={editRole} onChange={e => setEditRole(e.target.value)}>
                                        <option value="STUDENT">{t("admin.roleStudent")}</option>
                                        <option value="TEACHER">{t("admin.roleTeacher")}</option>
                                        <option value="ADMIN">{t("admin.roleAdmin")}</option>
                                    </select>
                                </div>
                            </div>
                            <div className="modal-footer">
                                <button className="btn btn-secondary" onClick={() => setEditUser(null)}>{t("admin.cancel")}</button>
                                <button className="btn btn-primary" onClick={handleEditSave} disabled={editSaving}>
                                    {editSaving ? t("admin.saving") : t("admin.save")}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}

            {/* Delete Confirm Modal */}
            {deleteUser && (
                <div className="modal d-block" tabIndex={-1} style={{ backgroundColor: "rgba(0,0,0,0.5)" }}>
                    <div className="modal-dialog">
                        <div className="modal-content">
                            <div className="modal-header">
                                <h5 className="modal-title">{t("admin.deleteTitle")}</h5>
                                <button className="btn-close" onClick={() => setDeleteUser(null)}></button>
                            </div>
                            <div className="modal-body">
                                <p>
                                    {t("admin.deleteConfirm", { name: `${deleteUser.firstName} ${deleteUser.lastName}`, email: deleteUser.email })}
                                </p>
                                <p className="text-danger">{t("admin.deleteWarning")}</p>
                            </div>
                            <div className="modal-footer">
                                <button className="btn btn-secondary" onClick={() => setDeleteUser(null)}>{t("admin.cancel")}</button>
                                <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
                                    {deleting ? t("admin.deleting") : t("admin.deleteFinal")}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
