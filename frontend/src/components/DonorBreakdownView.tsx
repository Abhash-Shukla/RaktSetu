import React, { useState } from "react";
import type { RankedMatchDto } from "../api/client";
import { ChevronDown, ChevronUp, AlertTriangle, ShieldCheck, MapPin, Sparkles, Send, Clock, UserCheck, UserX } from "lucide-react";

interface DonorBreakdownViewProps {
  matches: RankedMatchDto[];
  onNotifyDonor?: (donorId: number) => void;
  onRecordResponse?: (matchLogId: number, outcome: "DECLINED" | "EXPIRED" | "ACCEPTED") => void;
  activeMatchLogId?: number | null;
  actionLoading?: boolean;
}

export const DonorBreakdownView: React.FC<DonorBreakdownViewProps> = ({
  matches,
  onNotifyDonor,
  onRecordResponse,
  activeMatchLogId,
  actionLoading = false,
}) => {
  const [expandedId, setExpandedId] = useState<number | null>(matches[0]?.donorId ?? null);

  const toggleExpand = (id: number) => {
    setExpandedId((prev) => (prev === id ? null : id));
  };

  const formatScore = (val: number | undefined) => {
    if (val === undefined || isNaN(val)) return "0.000";
    return Number(val).toFixed(3);
  };

  const getFactorBarClass = (factorName: string) => {
    const f = factorName.toLowerCase();
    if (f.includes("proximity")) return "fill-proximity";
    if (f.includes("rarity")) return "fill-rarity";
    if (f.includes("responsiveness")) return "fill-responsiveness";
    return "fill-proximity";
  };

  if (!matches || matches.length === 0) {
    return (
      <div className="card" style={{ textAlign: "center", padding: "40px", color: "var(--text-muted)" }}>
        <AlertTriangle size={32} style={{ margin: "0 auto 12px auto", color: "var(--warning)" }} />
        <div style={{ fontWeight: 600, fontSize: "1rem" }}>No eligible donors found</div>
        <div style={{ fontSize: "0.85rem", marginTop: "4px" }}>
          All compatible donors may currently be in statutory cooldown, paused, or exhausted.
        </div>
      </div>
    );
  }

  return (
    <div className="donor-matches-list" data-testid="donor-matches-list">
      {matches.map((m, idx) => {
        const donorId = m.donorId ?? (idx + 1);
        const isExpanded = expandedId === donorId;
        const explanation = m.plainLanguageExplanation || "";
        const reasonCode = m.reasonCode || "";
        const isStale = explanation.includes("STALE_LOCATION") || reasonCode.includes("STALE");
        const hasRareFlag = explanation.toLowerCase().includes("rare") || reasonCode.includes("RARITY");
        const totalScore = m.totalScore ?? 0;
        const contributions = m.factorContributions || {};

        return (
          <div
            key={donorId}
            className="card"
            data-testid={`match-card-${donorId}`}
            style={{
              padding: "16px 20px",
              marginBottom: "14px",
              border: idx === 0 ? "2px solid var(--primary)" : "1px solid var(--border)",
              position: "relative",
            }}
          >
            {idx === 0 && (
              <span
                style={{
                  position: "absolute",
                  top: "-10px",
                  left: "20px",
                  background: "var(--primary)",
                  color: "white",
                  fontSize: "0.72rem",
                  fontWeight: 700,
                  padding: "2px 8px",
                  borderRadius: "4px",
                  textTransform: "uppercase",
                  letterSpacing: "0.04em",
                }}
              >
                Top Match
              </span>
            )}

            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
                <div
                  style={{
                    width: "36px",
                    height: "36px",
                    borderRadius: "50%",
                    background: idx === 0 ? "var(--primary-light)" : "#f1f5f9",
                    color: idx === 0 ? "var(--primary-dark)" : "var(--text-muted)",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    fontWeight: 700,
                    fontSize: "0.95rem",
                  }}
                >
                  #{m.rankPosition ?? (idx + 1)}
                </div>

                <div>
                  <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <span style={{ fontWeight: 700, fontSize: "1.05rem" }}>
                      Donor #{donorId}
                    </span>
                    <span className="badge badge-neutral" style={{ fontWeight: 700 }}>
                      {m.bloodGroup}
                    </span>
                    <span style={{ fontSize: "0.85rem", color: "var(--text-muted)" }}>
                      Zone: {m.zone}
                    </span>
                  </div>

                  {/* Badges / Flags */}
                  <div style={{ display: "flex", alignItems: "center", gap: "6px", marginTop: "4px" }}>
                    {isStale && (
                      <span className="badge badge-warning" data-testid="stale-location-badge">
                        <MapPin size={11} /> STALE_LOCATION (Home Zone Used)
                      </span>
                    )}
                    {hasRareFlag && (
                      <span className="badge badge-neutral" style={{ background: "#ede9fe", color: "#6d28d9" }} data-testid="rare-type-badge">
                        <Sparkles size={11} /> RARE_TYPE_CONSERVED
                      </span>
                    )}
                    {reasonCode && (
                      <span className="badge badge-neutral" style={{ fontSize: "0.72rem" }}>
                        {reasonCode}
                      </span>
                    )}
                  </div>
                </div>
              </div>

              <div style={{ display: "flex", alignItems: "center", gap: "16px" }}>
                <div style={{ textAlign: "right" }}>
                  <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>
                    Composite Score
                  </div>
                  <div style={{ fontSize: "1.2rem", fontWeight: 800, color: "var(--primary)" }} data-testid={`match-score-${donorId}`}>
                    {formatScore(totalScore)}
                  </div>
                </div>

                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ padding: "6px 10px" }}
                  onClick={() => toggleExpand(donorId)}
                  aria-expanded={isExpanded}
                  data-testid={`toggle-expand-${donorId}`}
                >
                  {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                </button>
              </div>
            </div>

            {/* Plain-Language Explanation */}
            <div
              style={{
                marginTop: "12px",
                padding: "10px 14px",
                backgroundColor: "#f8fafc",
                borderRadius: "var(--radius-sm)",
                border: "1px solid var(--border)",
                fontSize: "0.88rem",
                display: "flex",
                alignItems: "flex-start",
                gap: "8px",
              }}
              data-testid={`explanation-${donorId}`}
            >
              <ShieldCheck size={16} style={{ color: "var(--primary)", flexShrink: 0, marginTop: "2px" }} />
              <div>
                <strong>Explanation:</strong> {explanation}
              </div>
            </div>

            {/* Expandable Per-Factor Breakdown */}
            {isExpanded && (
              <div
                style={{
                  marginTop: "14px",
                  paddingTop: "14px",
                  borderTop: "1px dashed var(--border)",
                }}
                data-testid={`factor-breakdown-${donorId}`}
              >
                <div style={{ fontSize: "0.82rem", fontWeight: 700, color: "var(--text-muted)", textTransform: "uppercase", marginBottom: "8px" }}>
                  Traceable Per-Factor Breakdown (Contributions sum to score):
                </div>

                <div className="table-responsive">
                  <table className="data-table" style={{ fontSize: "0.84rem" }}>
                    <thead>
                      <tr>
                        <th>Factor</th>
                        <th>Contribution</th>
                        <th>Relative Weight / Share</th>
                      </tr>
                    </thead>
                    <tbody>
                      {Object.entries(contributions).map(([factorName, contribution]) => {
                        const pct = Math.max(0, Math.min(100, Math.round((contribution / Math.max(0.001, totalScore)) * 100)));
                        return (
                          <tr key={factorName} data-testid={`factor-row-${factorName.toLowerCase()}`}>
                            <td><strong>{factorName}</strong></td>
                            <td style={{ fontWeight: 700, color: "var(--primary)" }}>
                              +{formatScore(contribution)}
                            </td>
                            <td style={{ width: "45%" }}>
                              <div className="factor-bar-container">
                                <div className="factor-bar-track">
                                  <div
                                    className={`factor-bar-fill ${getFactorBarClass(factorName)}`}
                                    style={{ width: `${pct}%` }}
                                  />
                                </div>
                                <span style={{ fontSize: "0.75rem", color: "var(--text-muted)", width: "32px" }}>
                                  {pct}%
                                </span>
                              </div>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                {/* Outreach & Fallback Actions */}
                <div style={{ display: "flex", gap: "10px", marginTop: "14px", justifyContent: "flex-end" }}>
                  {onNotifyDonor && (
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => onNotifyDonor(donorId)}
                      disabled={actionLoading}
                      data-testid={`notify-donor-btn-${donorId}`}
                    >
                      <Send size={14} /> Send Outreach Notification
                    </button>
                  )}

                  {activeMatchLogId && onRecordResponse && (
                    <>
                      <button
                        type="button"
                        className="btn btn-danger"
                        onClick={() => onRecordResponse(activeMatchLogId, "DECLINED")}
                        disabled={actionLoading}
                        data-testid="record-decline-btn"
                      >
                        <UserX size={14} /> Record Decline (Next Match)
                      </button>
                      <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => onRecordResponse(activeMatchLogId, "EXPIRED")}
                        disabled={actionLoading}
                        data-testid="record-no-response-btn"
                      >
                        <Clock size={14} /> Mark No-Response
                      </button>
                      <button
                        type="button"
                        className="btn btn-success"
                        onClick={() => onRecordResponse(activeMatchLogId, "ACCEPTED")}
                        disabled={actionLoading}
                        data-testid="record-accept-btn"
                      >
                        <UserCheck size={14} /> Record Accepted
                      </button>
                    </>
                  )}
                </div>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};
