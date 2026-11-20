import React, { useState, useEffect } from "react";
import { useParams, Link } from "react-router-dom";
import { api, type RankedMatchDto, type BloodRequestResponseDto } from "../api/client";
import { DonorBreakdownView } from "../components/DonorBreakdownView";
import { ArrowLeft, RefreshCw, AlertCircle, CheckCircle2, UserCheck, Shield, Activity } from "lucide-react";

export const StaffRankedMatchesPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const requestId = Number(id);

  const [request, setRequest] = useState<BloodRequestResponseDto | null>(null);
  const [matches, setMatches] = useState<RankedMatchDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [activeMatchLogId, setActiveMatchLogId] = useState<number | null>(null);

  const fetchData = async () => {
    if (!requestId || isNaN(requestId)) return;
    setLoading(true);
    setError(null);

    try {
      const [req, matchList] = await Promise.all([
        api.getRequestById(requestId),
        api.getMatches(requestId),
      ]);
      setRequest(req);
      setMatches(matchList);
    } catch (err: any) {
      setError(err.message || "Failed to load matches");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [requestId]);

  const handleNotifyDonor = async (donorId: number) => {
    setActionLoading(true);
    setStatusMessage(null);
    setError(null);

    try {
      const notif = await api.notifyDonor(requestId, donorId);
      setStatusMessage(`Outreach notification sent to Donor #${donorId}. Waiting for donor response.`);
      // Re-fetch matches to refresh states
      const refreshedMatches = await api.getMatches(requestId);
      setMatches(refreshedMatches);
      if (notif?.matchLogId != null) {
        setActiveMatchLogId(notif.matchLogId);
      }
    } catch (err: any) {
      setError(err.message || "Failed to notify donor");
    } finally {
      setActionLoading(false);
    }
  };

  const handleRecordResponse = async (matchLogId: number, outcome: "DECLINED" | "EXPIRED" | "ACCEPTED") => {
    setActionLoading(true);
    setStatusMessage(null);
    setError(null);

    try {
      await api.recordDonorResponse(matchLogId, outcome);
      setStatusMessage(`Recorded response: ${outcome}. System re-ranked and surfaced next-best candidate.`);
      // Re-fetch matches to surface next-best match
      const refreshedMatches = await api.getMatches(requestId);
      setMatches(refreshedMatches);
      setActiveMatchLogId(null);
    } catch (err: any) {
      setError(err.message || "Failed to record response");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: "center", padding: "60px", color: "var(--text-muted)" }}>
        <RefreshCw size={28} className="animate-spin" style={{ margin: "0 auto 12px auto" }} />
        <div>Computing compatible donor ranking and explainability factors...</div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "20px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "12px" }}>
          <Link to="/staff/review" className="btn btn-secondary" style={{ padding: "6px 12px" }}>
            <ArrowLeft size={16} /> Back to Queue
          </Link>
          <div>
            <h1 style={{ fontSize: "1.5rem", fontWeight: 800 }}>
              Ranked Matches for Request #{requestId}
            </h1>
            <div style={{ fontSize: "0.85rem", color: "var(--text-muted)" }}>
              Urgency: <strong>{request?.urgency}</strong> • Group: <strong>{request?.bloodGroup}</strong> • Zone: <strong>{request?.zone}</strong> • Units: <strong>{request?.unitsRequested}</strong>
            </div>
          </div>
        </div>

        <button onClick={fetchData} className="btn btn-secondary">
          <RefreshCw size={15} /> Refresh Ranking
        </button>
      </div>

      {statusMessage && (
        <div className="alert alert-success" role="status">
          <CheckCircle2 size={18} />
          <div>{statusMessage}</div>
        </div>
      )}

      {error && (
        <div className="alert alert-danger" role="alert">
          <AlertCircle size={18} />
          <div>{error}</div>
        </div>
      )}

      {/* Request Overview Header Card */}
      <div className="card" style={{ padding: "16px 20px", marginBottom: "20px", background: "#ffffff" }}>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: "16px" }}>
          <div>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>Request Status</div>
            <div style={{ fontWeight: 700, fontSize: "1.05rem" }}>
              <span className="badge badge-success">{request?.status}</span>
            </div>
          </div>
          <div>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>Weight Profile</div>
            <div style={{ fontWeight: 600, fontSize: "0.95rem" }}>
              {request?.urgency || "URGENT"}
            </div>
          </div>
          <div>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>Algorithm</div>
            <div style={{ fontWeight: 600, fontSize: "0.95rem" }}>
              Greedy Top-N (S6)
            </div>
          </div>
          <div>
            <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>Eligible Candidates</div>
            <div style={{ fontWeight: 700, fontSize: "1.05rem", color: "var(--primary)" }}>
              {matches.length}
            </div>
          </div>
        </div>
      </div>

      {/* Main Donor Breakdown Component */}
      <DonorBreakdownView
        matches={matches}
        onNotifyDonor={handleNotifyDonor}
        onRecordResponse={handleRecordResponse}
        activeMatchLogId={activeMatchLogId}
        actionLoading={actionLoading}
      />
    </div>
  );
};
