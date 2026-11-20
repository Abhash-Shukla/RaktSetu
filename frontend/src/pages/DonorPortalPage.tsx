import React, { useState, useEffect } from "react";
import { api, type DonorProfileDto, type NotificationResponseDto } from "../api/client";
import { User, Bell, MapPin, Calendar, Heart, Shield, Check, X, RefreshCw, AlertCircle, CheckCircle } from "lucide-react";

export const DonorPortalPage: React.FC = () => {
  const [profile, setProfile] = useState<DonorProfileDto | null>(null);
  const [notifications, setNotifications] = useState<NotificationResponseDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [actionLoading, setActionLoading] = useState(false);

  // Profile update fields
  const [isAvailable, setIsAvailable] = useState(true);
  const [currentZone, setCurrentZone] = useState("");

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [p, notifs] = await Promise.all([
        api.getDonorProfile(),
        api.getDonorNotifications(),
      ]);
      setProfile(p);
      setIsAvailable(p.isAvailable ?? true);
      setCurrentZone(p.currentZone || p.homeZone || "");
      setNotifications(notifs);
    } catch (err: any) {
      setError(err.message || "Failed to load donor profile");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleToggleAvailability = async () => {
    if (!profile) return;
    setActionLoading(true);
    setSuccessMsg(null);
    setError(null);

    const newAvailable = !isAvailable;
    try {
      const updated = await api.updateDonorProfile({
        isAvailable: newAvailable,
        currentZone: currentZone || undefined,
      });
      setProfile(updated);
      setIsAvailable(updated.isAvailable ?? true);
      setSuccessMsg(`Availability updated to: ${newAvailable ? "Active (Available for matching)" : "Temporarily Inactive"}`);
    } catch (err: any) {
      setError(err.message || "Failed to update availability");
    } finally {
      setActionLoading(false);
    }
  };

  const handleUpdateLocation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!profile) return;
    setActionLoading(true);
    setSuccessMsg(null);
    setError(null);

    try {
      const updated = await api.updateDonorProfile({
        isAvailable: isAvailable,
        currentZone: currentZone.trim(),
      });
      setProfile(updated);
      setSuccessMsg(`Current location confirmed in zone: ${updated.currentZone}. Timestamp refreshed.`);
    } catch (err: any) {
      setError(err.message || "Failed to update location");
    } finally {
      setActionLoading(false);
    }
  };

  const handleRespondNotification = async (matchLogId: number, response: "ACCEPTED" | "DECLINED") => {
    setActionLoading(true);
    setSuccessMsg(null);
    setError(null);

    try {
      await api.respondToNotification(matchLogId, { accept: response === "ACCEPTED" });
      setSuccessMsg(`You have ${response.toLowerCase()} the blood donation request. Thank you for your response!`);
      // Refresh notifications
      const notifs = await api.getDonorNotifications();
      setNotifications(notifs);
    } catch (err: any) {
      setError(err.message || "Failed to submit response");
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: "center", padding: "60px", color: "var(--text-muted)" }}>
        <RefreshCw size={28} className="animate-spin" style={{ margin: "0 auto 12px auto" }} />
        <div>Loading your donor dashboard...</div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: "860px", margin: "0 auto" }}>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "24px" }}>
        <div>
          <h1 style={{ fontSize: "1.6rem", fontWeight: 800 }}>Voluntary Donor Portal</h1>
          <p style={{ color: "var(--text-muted)", fontSize: "0.92rem", marginTop: "4px" }}>
            Manage your donation availability, confirmed location, and respond to urgent hospital outreach notifications.
          </p>
        </div>
        <button onClick={fetchData} className="btn btn-secondary" disabled={loading}>
          <RefreshCw size={15} /> Refresh
        </button>
      </div>

      {successMsg && (
        <div className="alert alert-success" role="status">
          <CheckCircle size={18} />
          <div>{successMsg}</div>
        </div>
      )}

      {error && (
        <div className="alert alert-danger" role="alert">
          <AlertCircle size={18} />
          <div>{error}</div>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "1.2fr 1fr", gap: "24px" }}>
        {/* Left Column: Profile & Settings */}
        <div>
          <div className="card">
            <div className="card-header">
              <div className="card-title">
                <User size={18} /> Donor Profile
              </div>
              <span className={`badge ${profile?.isAvailable ? "badge-success" : "badge-neutral"}`}>
                {profile?.isAvailable ? "Available" : "Paused"}
              </span>
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "20px" }}>
              <div style={{ background: "#f8fafc", padding: "12px", borderRadius: "var(--radius-sm)", border: "1px solid var(--border)" }}>
                <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>Blood Phenotype</div>
                <div style={{ fontSize: "1.4rem", fontWeight: 800, color: "var(--primary)" }}>
                  {profile?.bloodGroup}
                </div>
              </div>

              <div style={{ background: "#f8fafc", padding: "12px", borderRadius: "var(--radius-sm)", border: "1px solid var(--border)" }}>
                <div style={{ fontSize: "0.75rem", color: "var(--text-muted)", textTransform: "uppercase" }}>Biological Sex</div>
                <div style={{ fontSize: "1.1rem", fontWeight: 700 }}>
                  {profile?.sex}
                </div>
                <div style={{ fontSize: "0.72rem", color: "var(--text-muted)", marginTop: "2px" }}>
                  {profile?.sex === "FEMALE" ? "120-day cooldown" : "90-day cooldown"}
                </div>
              </div>
            </div>

            <div style={{ fontSize: "0.82rem", color: "var(--text-muted)", marginBottom: "16px", lineHeight: "1.5" }}>
              * Under National Blood Transfusion Council (NBTC) guidelines, biological sex is used strictly to select the cooldown interval (90 days male, 120 days female).
            </div>

            <div style={{ borderTop: "1px solid var(--border)", paddingTop: "16px", marginBottom: "16px" }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
                <div>
                  <div style={{ fontWeight: 600, fontSize: "0.92rem" }}>Donation Availability Toggle</div>
                  <div style={{ fontSize: "0.8rem", color: "var(--text-muted)" }}>
                    Turn off if traveling, unwell, or temporarily unable to donate.
                  </div>
                </div>
                <button
                  type="button"
                  onClick={handleToggleAvailability}
                  className={`btn ${isAvailable ? "btn-success" : "btn-secondary"}`}
                  style={{ minWidth: "110px" }}
                  disabled={actionLoading}
                >
                  {isAvailable ? "Active" : "Inactive"}
                </button>
              </div>
            </div>

            {/* Location Confirmation Form */}
            <form onSubmit={handleUpdateLocation} style={{ borderTop: "1px solid var(--border)", paddingTop: "16px" }}>
              <div className="form-group">
                <label className="form-label" style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                  <MapPin size={15} /> Confirm Current Zone
                </label>
                <div style={{ display: "flex", gap: "8px" }}>
                  <input
                    type="text"
                    className="form-input"
                    value={currentZone}
                    onChange={(e) => setCurrentZone(e.target.value)}
                    placeholder="e.g. Z01, Z02"
                    required
                  />
                  <button type="submit" className="btn btn-secondary" disabled={actionLoading}>
                    Confirm
                  </button>
                </div>
                <div className="form-hint">
                  Fresh location within 4 hours is trusted for distance scoring; older locations are flagged as STALE_LOCATION and default to home zone.
                </div>
              </div>
            </form>
          </div>

          {/* History Statistics */}
          <div className="card">
            <div className="card-title" style={{ marginBottom: "14px", fontSize: "1rem" }}>
              <Heart size={16} /> Outreach History Statistics
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "10px", textAlign: "center" }}>
              <div style={{ background: "#f8fafc", padding: "10px", borderRadius: "6px" }}>
                <div style={{ fontSize: "1.2rem", fontWeight: 700 }}>{profile?.totalContacts || 0}</div>
                <div style={{ fontSize: "0.72rem", color: "var(--text-muted)" }}>Contacts</div>
              </div>
              <div style={{ background: "var(--success-bg)", padding: "10px", borderRadius: "6px", color: "var(--success)" }}>
                <div style={{ fontSize: "1.2rem", fontWeight: 700 }}>{profile?.totalAccepts || 0}</div>
                <div style={{ fontSize: "0.72rem" }}>Accepts</div>
              </div>
              <div style={{ background: "#fff1f2", padding: "10px", borderRadius: "6px", color: "#be123c" }}>
                <div style={{ fontSize: "1.2rem", fontWeight: 700 }}>{profile?.totalDeclines || 0}</div>
                <div style={{ fontSize: "0.72rem" }}>Declines</div>
              </div>
              <div style={{ background: "#f1f5f9", padding: "10px", borderRadius: "6px", color: "#475569" }}>
                <div style={{ fontSize: "1.2rem", fontWeight: 700 }}>{profile?.totalNoResponses || 0}</div>
                <div style={{ fontSize: "0.72rem" }}>No-Response</div>
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: In-App Notifications Queue */}
        <div>
          <div className="card" style={{ minHeight: "380px" }}>
            <div className="card-header">
              <div className="card-title">
                <Bell size={18} /> In-App Outreach Notifications ({notifications.length})
              </div>
            </div>

            {notifications.length === 0 ? (
              <div style={{ padding: "40px 20px", textAlign: "center", color: "var(--text-muted)" }}>
                <Bell size={32} style={{ margin: "0 auto 12px auto", opacity: 0.4 }} />
                <div style={{ fontWeight: 600 }}>No active blood requests</div>
                <div style={{ fontSize: "0.85rem", marginTop: "4px" }}>
                  You will receive an alert here when an urgent compatible requirement is approved in your area.
                </div>
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
                {notifications.map((n) => (
                  <div
                    key={n.matchLogId}
                    style={{
                      border: "1px solid #fed7aa",
                      background: "#fffbeb",
                      borderRadius: "var(--radius-sm)",
                      padding: "16px",
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "8px" }}>
                      <span className="badge badge-critical" style={{ fontSize: "0.72rem" }}>
                        {n.urgency} REQUIREMENT
                      </span>
                      <span style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>
                        Req #{n.requestId}
                      </span>
                    </div>

                    <div style={{ fontWeight: 700, fontSize: "0.95rem", color: "#9a3412", marginBottom: "6px" }}>
                      Blood Needed: {n.bloodGroup} {n.patientReference ? `(${n.patientReference})` : ""}
                    </div>

                    <div style={{ fontSize: "0.84rem", color: "#78350f", marginBottom: "14px" }}>
                      Hospital Location: <strong>{n.zone || "Local Hospital"}</strong>
                      {n.deadline && (
                        <div>Response Window: {new Date(n.deadline).toLocaleTimeString()}</div>
                      )}
                    </div>

                    <div style={{ display: "flex", gap: "8px" }}>
                      <button
                        type="button"
                        className="btn btn-success"
                        style={{ flex: 1, padding: "7px 12px", fontSize: "0.85rem" }}
                        onClick={() => n.matchLogId && handleRespondNotification(n.matchLogId, "ACCEPTED")}
                        disabled={actionLoading || !n.matchLogId}
                      >
                        <Check size={14} /> Accept to Donate
                      </button>
                      <button
                        type="button"
                        className="btn btn-danger"
                        style={{ flex: 1, padding: "7px 12px", fontSize: "0.85rem" }}
                        onClick={() => n.matchLogId && handleRespondNotification(n.matchLogId, "DECLINED")}
                        disabled={actionLoading || !n.matchLogId}
                      >
                        <X size={14} /> Unable to Donate
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
