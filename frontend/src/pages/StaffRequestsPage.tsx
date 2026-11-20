import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { api, type BloodRequestResponseDto } from "../api/client";
import { Activity, ArrowRight, RefreshCw, AlertCircle } from "lucide-react";

export const StaffRequestsPage: React.FC = () => {
  const [requests, setRequests] = useState<BloodRequestResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const fetchRequests = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getAllRequests();
      setRequests(data);
    } catch (err: any) {
      setError(err.message || "Failed to load blood requests");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRequests();
  }, []);

  const getUrgencyBadge = (u: string) => {
    switch (u) {
      case "CRITICAL": return <span className="badge badge-critical">CRITICAL</span>;
      case "URGENT": return <span className="badge badge-urgent">URGENT</span>;
      default: return <span className="badge badge-routine">ROUTINE</span>;
    }
  };

  const getStatusBadge = (s: string) => {
    switch (s) {
      case "OPEN": return <span className="badge badge-success">OPEN</span>;
      case "PENDING_REVIEW": return <span className="badge badge-warning">PENDING REVIEW</span>;
      case "RESOLVED_TIER0": return <span className="badge badge-neutral" style={{ background: "#e0f2fe", color: "#0369a1" }}>RESOLVED TIER 0</span>;
      case "FULFILLED": return <span className="badge badge-success">FULFILLED</span>;
      case "REJECTED": return <span className="badge badge-neutral" style={{ background: "#fee2e2", color: "#991b1b" }}>REJECTED</span>;
      default: return <span className="badge badge-neutral">{s}</span>;
    }
  };

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "24px" }}>
        <div>
          <h1 style={{ fontSize: "1.6rem", fontWeight: 800 }}>Active Blood Requests</h1>
          <p style={{ color: "var(--text-muted)", fontSize: "0.92rem", marginTop: "4px" }}>
            Track all institutional blood requirements, review status, and initiate donor matching.
          </p>
        </div>
        <button onClick={fetchRequests} className="btn btn-secondary">
          <RefreshCw size={15} /> Refresh
        </button>
      </div>

      {error && (
        <div className="alert alert-danger" role="alert">
          <AlertCircle size={18} />
          <div>{error}</div>
        </div>
      )}

      <div className="card" style={{ padding: "0" }}>
        {loading ? (
          <div style={{ padding: "40px", textAlign: "center", color: "var(--text-muted)" }}>
            Loading requests...
          </div>
        ) : requests.length === 0 ? (
          <div style={{ padding: "40px", textAlign: "center", color: "var(--text-muted)" }}>
            No blood requests found.
          </div>
        ) : (
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Req ID</th>
                  <th>Blood Group</th>
                  <th>Units</th>
                  <th>Urgency</th>
                  <th>Zone</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {requests.map((r) => (
                  <tr key={r.id}>
                    <td><strong>#{r.id}</strong></td>
                    <td>
                      <span className="badge badge-neutral" style={{ fontWeight: 700 }}>
                        {r.bloodGroup}
                      </span>
                    </td>
                    <td>{r.unitsRequested}</td>
                    <td>{getUrgencyBadge(r.urgency || "URGENT")}</td>
                    <td>{r.zone}</td>
                    <td>{getStatusBadge(r.status || "OPEN")}</td>
                    <td style={{ fontSize: "0.82rem", color: "var(--text-muted)" }}>
                      {r.createdAt ? new Date(r.createdAt).toLocaleString() : "—"}
                    </td>
                    <td>
                      {r.status === "OPEN" ? (
                        <Link
                          to={`/staff/requests/${r.id}/matches`}
                          className="btn btn-primary"
                          style={{ padding: "5px 12px", fontSize: "0.82rem" }}
                        >
                          Ranked Matches <ArrowRight size={13} />
                        </Link>
                      ) : r.status === "PENDING_REVIEW" ? (
                        <Link
                          to="/staff/review"
                          className="btn btn-secondary"
                          style={{ padding: "5px 12px", fontSize: "0.82rem" }}
                        >
                          Review in Queue
                        </Link>
                      ) : (
                        <span style={{ fontSize: "0.8rem", color: "var(--text-muted)" }}>
                          Completed
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
