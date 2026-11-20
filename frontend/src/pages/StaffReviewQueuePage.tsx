import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { api, type BloodRequestResponseDto, type Tier0CheckDto } from "../api/client";
import { ClipboardList, CheckCircle, XCircle, Search, Building2, AlertTriangle, ArrowRight, RefreshCw, FileText } from "lucide-react";

export const StaffReviewQueuePage: React.FC = () => {
  const [requests, setRequests] = useState<BloodRequestResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Selected request for review
  const [selectedReq, setSelectedReq] = useState<BloodRequestResponseDto | null>(null);
  const [tier0Result, setTier0Result] = useState<Tier0CheckDto | null>(null);
  const [checkingTier0, setCheckingTier0] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  // Editable review fields
  const [verifiedGroup, setVerifiedGroup] = useState("");
  const [verifiedZone, setVerifiedZone] = useState("");
  const [verifiedReference, setVerifiedReference] = useState("");
  const [notes, setNotes] = useState("");
  const [rejectReason, setRejectReason] = useState("");
  const [showRejectModal, setShowRejectModal] = useState(false);

  const fetchQueue = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getPendingRequests();
      setRequests(data);
      if (selectedReq) {
        const updated = data.find((r) => r.id === selectedReq.id);
        setSelectedReq(updated || null);
      }
    } catch (err: any) {
      setError(err.message || "Failed to load review queue");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchQueue();
  }, []);

  const openReview = async (req: BloodRequestResponseDto) => {
    setSelectedReq(req);
    setVerifiedGroup(req.bloodGroup || "O_POS");
    setVerifiedZone(req.zone || "Z01");
    setVerifiedReference(req.patientReference || "");
    setNotes("");
    setTier0Result(null);
    setActionMessage(null);
    setShowRejectModal(false);

    if (req.id != null) {
      // Trigger Tier 0 check automatically for this request
      setCheckingTier0(true);
      try {
        const t0 = await api.checkTier0(req.id);
        setTier0Result(t0);
      } catch {
        // Tier 0 check failure is handled gracefully
      } finally {
        setCheckingTier0(false);
      }
    }
  };

  const handleApprove = async () => {
    if (!selectedReq || selectedReq.id == null) return;
    setActionLoading(true);
    setActionMessage(null);
    setError(null);

    try {
      const updated = await api.reviewRequest(selectedReq.id, {
        approve: true,
        verifiedBloodGroup: verifiedGroup as any,
        verifiedZone: verifiedZone.trim(),
        verifiedPatientReference: verifiedReference.trim() || undefined,
      });
      setActionMessage(`Request #${selectedReq.id} approved into OPEN status.`);
      fetchQueue();
      setSelectedReq(updated);
    } catch (err: any) {
      setError(err.message || "Approval failed");
    } finally {
      setActionLoading(false);
    }
  };

  const handleReject = async () => {
    if (!selectedReq || selectedReq.id == null || !rejectReason.trim()) return;
    setActionLoading(true);
    setActionMessage(null);
    setError(null);

    try {
      await api.reviewRequest(selectedReq.id, {
        approve: false,
        rejectionReason: rejectReason.trim(),
      });
      setActionMessage(`Request #${selectedReq.id} rejected.`);
      setShowRejectModal(false);
      setSelectedReq(null);
      fetchQueue();
    } catch (err: any) {
      setError(err.message || "Rejection failed");
    } finally {
      setActionLoading(false);
    }
  };

  const handleFulfillTier0 = async (bankId: number) => {
    if (!selectedReq || selectedReq.id == null) return;
    setActionLoading(true);
    setActionMessage(null);
    try {
      const res = await api.fulfillTier0(selectedReq.id, bankId);
      setActionMessage(`Request #${selectedReq.id} fulfilled via Bank Inventory (Tier 0). No donors mobilized.`);
      fetchQueue();
      setSelectedReq(res);
    } catch (err: any) {
      setError(err.message || "Tier-0 fulfillment failed");
    } finally {
      setActionLoading(false);
    }
  };

  const getUrgencyBadge = (u: string) => {
    switch (u) {
      case "CRITICAL": return <span className="badge badge-critical">CRITICAL</span>;
      case "URGENT": return <span className="badge badge-urgent">URGENT</span>;
      default: return <span className="badge badge-routine">ROUTINE</span>;
    }
  };

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "24px" }}>
        <div>
          <h1 style={{ fontSize: "1.6rem", fontWeight: 800 }}>Staff Review Queue</h1>
          <p style={{ color: "var(--text-muted)", fontSize: "0.92rem", marginTop: "4px" }}>
            Inspect incoming blood requests, evaluate Tier-0 inventory stock, and approve or reject.
          </p>
        </div>
        <button onClick={fetchQueue} className="btn btn-secondary" disabled={loading}>
          <RefreshCw size={15} /> Refresh Queue
        </button>
      </div>

      {actionMessage && (
        <div className="alert alert-success" role="status">
          <CheckCircle size={18} />
          <div>{actionMessage}</div>
        </div>
      )}

      {error && (
        <div className="alert alert-danger" role="alert">
          <AlertTriangle size={18} />
          <div>{error}</div>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: selectedReq ? "1fr 1.2fr" : "1fr", gap: "24px" }}>
        {/* Left: Queue Table */}
        <div className="card" style={{ padding: "0" }}>
          <div style={{ padding: "16px 20px", borderBottom: "1px solid var(--border)", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
            <div style={{ fontWeight: 700, fontSize: "0.98rem", display: "flex", alignItems: "center", gap: "8px" }}>
              <ClipboardList size={18} /> Pending Review ({requests.length})
            </div>
          </div>

          {loading ? (
            <div style={{ padding: "40px", textAlign: "center", color: "var(--text-muted)" }}>
              Loading review queue...
            </div>
          ) : requests.length === 0 ? (
            <div style={{ padding: "48px 24px", textAlign: "center", color: "var(--text-muted)" }}>
              <CheckCircle size={32} style={{ color: "var(--success)", margin: "0 auto 12px auto" }} />
              <div style={{ fontWeight: 600 }}>Queue is completely clear!</div>
              <div style={{ fontSize: "0.85rem", marginTop: "4px" }}>No blood requests currently pending staff review.</div>
            </div>
          ) : (
            <div className="table-responsive">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Req ID</th>
                    <th>Group</th>
                    <th>Units</th>
                    <th>Urgency</th>
                    <th>Zone</th>
                    <th>Source</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {requests.map((r) => {
                    const isSelected = selectedReq?.id === r.id;
                    return (
                      <tr
                        key={r.id}
                        style={{
                          backgroundColor: isSelected ? "var(--primary-light)" : undefined,
                          cursor: "pointer",
                        }}
                        onClick={() => openReview(r)}
                      >
                        <td><strong>#{r.id}</strong></td>
                        <td>
                          <span className="badge badge-neutral" style={{ fontWeight: 700 }}>
                            {r.bloodGroup}
                          </span>
                        </td>
                        <td>{r.unitsRequested}</td>
                        <td>{getUrgencyBadge(r.urgency || "URGENT")}</td>
                        <td>{r.zone}</td>
                        <td>
                          <span style={{ fontSize: "0.78rem", color: "var(--text-muted)" }}>
                            {r.source}
                          </span>
                        </td>
                        <td>
                          <button
                            className="btn btn-secondary"
                            style={{ padding: "4px 10px", fontSize: "0.8rem" }}
                            onClick={(e) => {
                              e.stopPropagation();
                              openReview(r);
                            }}
                          >
                            Inspect
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Right: Review & Tier-0 Inspector */}
        {selectedReq && (
          <div className="card">
            <div className="card-header">
              <div>
                <div className="card-title">
                  Inspect Request #{selectedReq.id}
                </div>
                <div className="card-desc">
                  Received from {selectedReq.source} • Status: {selectedReq.status}
                </div>
              </div>
              <button
                className="btn btn-secondary"
                style={{ padding: "4px 8px" }}
                onClick={() => setSelectedReq(null)}
              >
                Close
              </button>
            </div>

            {/* Unparsed message text if parsed */}
            {selectedReq.unparsedText && (
              <div style={{ background: "#f8fafc", padding: "12px", borderRadius: "var(--radius-sm)", border: "1px solid var(--border)", marginBottom: "16px", fontSize: "0.85rem" }}>
                <div style={{ fontWeight: 600, color: "var(--text-muted)", fontSize: "0.75rem", textTransform: "uppercase", marginBottom: "4px" }}>
                  Original Message Text
                </div>
                <div style={{ fontStyle: "italic", color: "#334155" }}>"{selectedReq.unparsedText}"</div>
                {selectedReq.extractionConfidence != null && (
                  <div style={{ marginTop: "6px", fontSize: "0.78rem", color: "var(--info)" }}>
                    Confidence: {Math.round(selectedReq.extractionConfidence * 100)}%
                  </div>
                )}
              </div>
            )}

            {/* Tier-0 Inventory Check Card */}
            <div style={{ background: "#f0f9ff", border: "1px solid #bae6fd", borderRadius: "var(--radius-sm)", padding: "14px", marginBottom: "20px" }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "8px" }}>
                <div style={{ fontWeight: 700, color: "#0369a1", display: "flex", alignItems: "center", gap: "6px", fontSize: "0.92rem" }}>
                  <Building2 size={16} /> Tier-0 Blood Bank Inventory Check
                </div>
                {checkingTier0 && <span style={{ fontSize: "0.8rem", color: "#0369a1" }}>Querying banks...</span>}
              </div>

              {tier0Result ? (
                <div>
                  <div style={{ fontSize: "0.88rem", marginBottom: "8px" }}>
                    Compatible Units in Radius: <strong>{tier0Result.unitsAvailable ?? 0}</strong> (Required: {selectedReq.unitsRequested})
                  </div>

                  {tier0Result.resolved && tier0Result.bankId != null ? (
                    <div style={{ background: "#ffffff", padding: "10px", borderRadius: "6px", border: "1px solid #7dd3fc" }}>
                      <div style={{ color: "#0284c7", fontWeight: 600, fontSize: "0.85rem", marginBottom: "4px" }}>
                        Sufficient stock found at {tier0Result.bankName || "Blood Bank"}!
                      </div>
                      <div style={{ fontSize: "0.82rem", color: "var(--text-muted)", marginBottom: "8px" }}>
                        Zone: {tier0Result.bankZone} • Units Available: {tier0Result.unitsAvailable}
                      </div>
                      {selectedReq.status === "PENDING_REVIEW" || selectedReq.status === "OPEN" ? (
                        <button
                          onClick={() => handleFulfillTier0(tier0Result.bankId!)}
                          className="btn btn-success"
                          style={{ padding: "6px 14px", fontSize: "0.82rem" }}
                          disabled={actionLoading}
                        >
                          Fulfill via Tier 0 (Route to Bank)
                        </button>
                      ) : null}
                    </div>
                  ) : (
                    <div style={{ fontSize: "0.82rem", color: "#64748b" }}>
                      {tier0Result.message || "Insufficient compatible units in local bank inventory. Proceed to donor matching."}
                    </div>
                  )}
                </div>
              ) : (
                <div style={{ fontSize: "0.82rem", color: "#64748b" }}>
                  Tier-0 inventory check checks local bank stocks before mobilizing donors.
                </div>
              )}
            </div>

            {/* Editable fields */}
            {selectedReq.status === "PENDING_REVIEW" ? (
              <div>
                <div style={{ fontSize: "0.88rem", fontWeight: 700, marginBottom: "12px", color: "var(--text-main)" }}>
                  Verify & Correct Request Details:
                </div>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
                  <div className="form-group">
                    <label className="form-label">Verified Blood Group</label>
                    <select
                      className="form-select"
                      value={verifiedGroup}
                      onChange={(e) => setVerifiedGroup(e.target.value)}
                    >
                      <option value="A_POS">A+</option>
                      <option value="A_NEG">A-</option>
                      <option value="B_POS">B+</option>
                      <option value="B_NEG">B-</option>
                      <option value="AB_POS">AB+</option>
                      <option value="AB_NEG">AB-</option>
                      <option value="O_POS">O+</option>
                      <option value="O_NEG">O-</option>
                    </select>
                  </div>

                  <div className="form-group">
                    <label className="form-label">Verified Zone</label>
                    <input
                      type="text"
                      className="form-input"
                      value={verifiedZone}
                      onChange={(e) => setVerifiedZone(e.target.value)}
                    />
                  </div>
                </div>

                <div className="form-group">
                  <label className="form-label">Patient Reference</label>
                  <input
                    type="text"
                    className="form-input"
                    value={verifiedReference}
                    onChange={(e) => setVerifiedReference(e.target.value)}
                  />
                </div>

                <div className="form-group">
                  <label className="form-label">Staff Verification Notes</label>
                  <textarea
                    className="form-textarea"
                    rows={2}
                    placeholder="Enter review confirmation notes..."
                    value={notes}
                    onChange={(e) => setNotes(e.target.value)}
                  />
                </div>

                <div style={{ display: "flex", gap: "12px", marginTop: "16px" }}>
                  <button
                    onClick={handleApprove}
                    className="btn btn-primary"
                    style={{ flex: 1 }}
                    disabled={actionLoading}
                  >
                    <CheckCircle size={16} /> Approve & Open for Matching
                  </button>
                  <button
                    onClick={() => setShowRejectModal(true)}
                    className="btn btn-danger"
                    disabled={actionLoading}
                  >
                    <XCircle size={16} /> Reject
                  </button>
                </div>

                {showRejectModal && (
                  <div style={{ marginTop: "16px", padding: "14px", background: "var(--danger-bg)", borderRadius: "var(--radius-sm)", border: "1px solid #fca5a5" }}>
                    <div style={{ fontWeight: 600, color: "var(--danger)", fontSize: "0.85rem", marginBottom: "6px" }}>
                      State Reason for Rejection:
                    </div>
                    <input
                      type="text"
                      className="form-input"
                      placeholder="e.g. Duplicate requirement, invalid patient reference, out of service zone"
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                    />
                    <div style={{ display: "flex", gap: "8px", marginTop: "8px" }}>
                      <button
                        onClick={handleReject}
                        className="btn btn-danger"
                        style={{ padding: "6px 12px", fontSize: "0.82rem" }}
                        disabled={!rejectReason.trim() || actionLoading}
                      >
                        Confirm Rejection
                      </button>
                      <button
                        onClick={() => setShowRejectModal(false)}
                        className="btn btn-secondary"
                        style={{ padding: "6px 12px", fontSize: "0.82rem" }}
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              <div>
                <div className="alert alert-info" style={{ marginTop: "12px" }}>
                  Request status: <strong>{selectedReq.status}</strong>
                </div>
                {selectedReq.status === "OPEN" && (
                  <Link
                    to={`/staff/requests/${selectedReq.id}/matches`}
                    className="btn btn-primary"
                    style={{ width: "100%", justifyContent: "center" }}
                  >
                    View Ranked Matches & Explanations <ArrowRight size={16} />
                  </Link>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
