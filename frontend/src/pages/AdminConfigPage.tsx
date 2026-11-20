import React, { useState, useEffect } from "react";
import { api, type EngineConfigDto, type AuditLogDto, type UserSummaryDto } from "../api/client";
import { Shield, Settings, History, FileText, CheckCircle2, AlertCircle, RefreshCw, Save, Users } from "lucide-react";

export const AdminConfigPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"config" | "history" | "audit">("config");
  const [activeConfig, setActiveConfig] = useState<EngineConfigDto | null>(null);
  const [history, setHistory] = useState<EngineConfigDto[]>([]);
  const [auditLogs, setAuditLogs] = useState<AuditLogDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Form editable state
  const [proximityWeight, setProximityWeight] = useState(0.70);
  const [rarityWeight, setRarityWeight] = useState(0.30);
  const [responsivenessWeight, setResponsivenessWeight] = useState(0.0);
  const [cooldownDaysMale, setCooldownDaysMale] = useState(90);
  const [cooldownDaysFemale, setCooldownDaysFemale] = useState(120);
  const [freshnessWindowHours, setFreshnessWindowHours] = useState(4);
  const [topNCandidates, setTopNCandidates] = useState(5);
  const [notes, setNotes] = useState("");

  const fetchData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [cfg, hist, logs] = await Promise.all([
        api.getActiveConfig(),
        api.getConfigHistory(),
        api.getAuditLogs(),
      ]);

      setActiveConfig(cfg);
      setHistory(hist);
      setAuditLogs(logs);

      if (cfg && cfg.configJson) {
        try {
          const parsed = JSON.parse(cfg.configJson);
          if (parsed.proximityWeight !== undefined) setProximityWeight(parsed.proximityWeight);
          if (parsed.rarityWeight !== undefined) setRarityWeight(parsed.rarityWeight);
          if (parsed.responsivenessWeight !== undefined) setResponsivenessWeight(parsed.responsivenessWeight);
          if (parsed.cooldownDaysMale !== undefined) setCooldownDaysMale(parsed.cooldownDaysMale);
          if (parsed.cooldownDaysFemale !== undefined) setCooldownDaysFemale(parsed.cooldownDaysFemale);
          if (parsed.freshnessWindowHours !== undefined) setFreshnessWindowHours(parsed.freshnessWindowHours);
          if (parsed.topNCandidates !== undefined) setTopNCandidates(parsed.topNCandidates);
        } catch {
          // Keep defaults if parse fails
        }
      }
    } catch (err: any) {
      setError(err.message || "Failed to load administrative configuration");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleSaveConfig = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    setSuccessMsg(null);

    try {
      const configObj = {
        proximityWeight: Number(proximityWeight),
        rarityWeight: Number(rarityWeight),
        responsivenessWeight: Number(responsivenessWeight),
        cooldownDaysMale: Number(cooldownDaysMale),
        cooldownDaysFemale: Number(cooldownDaysFemale),
        freshnessWindowHours: Number(freshnessWindowHours),
        topNCandidates: Number(topNCandidates),
      };

      const newConfig: EngineConfigDto = {
        configJson: JSON.stringify(configObj),
        description: notes.trim() || "Administrative configuration update",
      };

      const saved = await api.saveConfig(newConfig);
      setActiveConfig(saved);
      setSuccessMsg(`New engine configuration version #${saved.versionNumber} created and activated!`);
      // Refresh history & audit
      const [hist, logs] = await Promise.all([api.getConfigHistory(), api.getAuditLogs()]);
      setHistory(hist);
      setAuditLogs(logs);
      setNotes("");
    } catch (err: any) {
      setError(err.message || "Failed to save configuration");
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: "center", padding: "60px", color: "var(--text-muted)" }}>
        <RefreshCw size={28} className="animate-spin" style={{ margin: "0 auto 12px auto" }} />
        <div>Loading system configuration and audit logs...</div>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "24px" }}>
        <div>
          <h1 style={{ fontSize: "1.6rem", fontWeight: 800 }}>Administrative Governance Console</h1>
          <p style={{ color: "var(--text-muted)", fontSize: "0.92rem", marginTop: "4px" }}>
            Tune engine factor weights, cooldown periods, review versioned history, and inspect system audit trails.
          </p>
        </div>
        <button onClick={fetchData} className="btn btn-secondary">
          <RefreshCw size={15} /> Refresh Console
        </button>
      </div>

      {successMsg && (
        <div className="alert alert-success" role="status">
          <CheckCircle2 size={18} />
          <div>{successMsg}</div>
        </div>
      )}

      {error && (
        <div className="alert alert-danger" role="alert">
          <AlertCircle size={18} />
          <div>{error}</div>
        </div>
      )}

      <div className="tabs-nav">
        <button
          type="button"
          className={`tab-btn ${activeTab === "config" ? "active" : ""}`}
          onClick={() => setActiveTab("config")}
        >
          <Settings size={16} style={{ verticalAlign: "text-bottom", marginRight: "6px" }} />
          Engine Configuration Editor
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === "history" ? "active" : ""}`}
          onClick={() => setActiveTab("history")}
        >
          <History size={16} style={{ verticalAlign: "text-bottom", marginRight: "6px" }} />
          Configuration Version History ({history.length})
        </button>
        <button
          type="button"
          className={`tab-btn ${activeTab === "audit" ? "active" : ""}`}
          onClick={() => setActiveTab("audit")}
        >
          <FileText size={16} style={{ verticalAlign: "text-bottom", marginRight: "6px" }} />
          System Audit Log ({auditLogs.length})
        </button>
      </div>

      {/* Tab 1: Config Editor */}
      {activeTab === "config" && (
        <div style={{ maxWidth: "800px" }}>
          <form onSubmit={handleSaveConfig} className="card">
            <div className="card-header">
              <div className="card-title">
                Active Version #{activeConfig?.versionNumber || 1}
              </div>
              <span className="badge badge-success">Active in Production</span>
            </div>

            <div style={{ fontSize: "0.9rem", fontWeight: 700, color: "var(--text-main)", marginBottom: "12px" }}>
              1. Base Factor Weights (Sum must equal 1.0 in standard specification)
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "16px", marginBottom: "20px" }}>
              <div className="form-group">
                <label className="form-label">Proximity Weight</label>
                <input
                  type="number"
                  step="0.05"
                  min="0"
                  max="1"
                  className="form-input"
                  value={proximityWeight}
                  onChange={(e) => setProximityWeight(parseFloat(e.target.value) || 0)}
                  required
                />
                <div className="form-hint">Road-distance decay score</div>
              </div>

              <div className="form-group">
                <label className="form-label">Rarity Weight</label>
                <input
                  type="number"
                  step="0.05"
                  min="0"
                  max="1"
                  className="form-input"
                  value={rarityWeight}
                  onChange={(e) => setRarityWeight(parseFloat(e.target.value) || 0)}
                  required
                />
                <div className="form-hint">Rare-type conservation penalty</div>
              </div>

              <div className="form-group">
                <label className="form-label">Responsiveness Weight</label>
                <input
                  type="number"
                  step="0.05"
                  min="0"
                  max="1"
                  className="form-input"
                  value={responsivenessWeight}
                  onChange={(e) => setResponsivenessWeight(parseFloat(e.target.value) || 0)}
                />
                <div className="form-hint">Optional factor (0.0 = OFF in S6)</div>
              </div>
            </div>

            <div style={{ fontSize: "0.9rem", fontWeight: 700, color: "var(--text-main)", marginBottom: "12px", borderTop: "1px solid var(--border)", paddingTop: "16px" }}>
              2. Cooldown Exclusion Windows (Hard Constraints)
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "20px" }}>
              <div className="form-group">
                <label className="form-label">Male Cooldown (Days)</label>
                <input
                  type="number"
                  min="30"
                  max="180"
                  className="form-input"
                  value={cooldownDaysMale}
                  onChange={(e) => setCooldownDaysMale(parseInt(e.target.value) || 90)}
                  required
                />
                <div className="form-hint">NBTC standard: 90 days</div>
              </div>

              <div className="form-group">
                <label className="form-label">Female Cooldown (Days)</label>
                <input
                  type="number"
                  min="30"
                  max="180"
                  className="form-input"
                  value={cooldownDaysFemale}
                  onChange={(e) => setCooldownDaysFemale(parseInt(e.target.value) || 120)}
                  required
                />
                <div className="form-hint">NBTC standard: 120 days</div>
              </div>
            </div>

            <div style={{ fontSize: "0.9rem", fontWeight: 700, color: "var(--text-main)", marginBottom: "12px", borderTop: "1px solid var(--border)", paddingTop: "16px" }}>
              3. Spatial Topology & Selection Heuristic
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px", marginBottom: "20px" }}>
              <div className="form-group">
                <label className="form-label">Location Freshness Window (Hours)</label>
                <input
                  type="number"
                  min="1"
                  max="72"
                  className="form-input"
                  value={freshnessWindowHours}
                  onChange={(e) => setFreshnessWindowHours(parseInt(e.target.value) || 4)}
                  required
                />
                <div className="form-hint">Older locations flagged STALE_LOCATION</div>
              </div>

              <div className="form-group">
                <label className="form-label">Top-N Candidate Count</label>
                <input
                  type="number"
                  min="1"
                  max="50"
                  className="form-input"
                  value={topNCandidates}
                  onChange={(e) => setTopNCandidates(parseInt(e.target.value) || 5)}
                  required
                />
                <div className="form-hint">Number of top ranked matches surfaced</div>
              </div>
            </div>

            <div className="form-group" style={{ borderTop: "1px solid var(--border)", paddingTop: "16px" }}>
              <label className="form-label">Version Changelog / Author Notes</label>
              <textarea
                className="form-textarea"
                rows={2}
                placeholder="State rationale for this configuration change..."
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>

            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: "100%", padding: "12px", marginTop: "12px" }}
              disabled={saving}
            >
              <Save size={16} />
              {saving ? "Creating Version..." : "Save & Activate New Configuration Version"}
            </button>
          </form>
        </div>
      )}

      {/* Tab 2: History */}
      {activeTab === "history" && (
        <div className="card" style={{ padding: "0" }}>
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Version</th>
                  <th>Status</th>
                  <th>Proximity / Rarity / Resp</th>
                  <th>Cooldown (M/F)</th>
                  <th>Freshness</th>
                  <th>Author</th>
                  <th>Created At</th>
                  <th>Description</th>
                </tr>
              </thead>
              <tbody>
                {history.map((h) => {
                  let parsedConfig: any = {};
                  try {
                    parsedConfig = h.configJson ? JSON.parse(h.configJson) : {};
                  } catch {}

                  return (
                    <tr key={h.versionNumber || Math.random()}>
                      <td><strong>v{h.versionNumber}</strong></td>
                      <td>
                        {h.isActive ? (
                          <span className="badge badge-success">ACTIVE</span>
                        ) : (
                          <span className="badge badge-neutral">SUPERSEDED</span>
                        )}
                      </td>
                      <td>
                        {(parsedConfig.proximityWeight ?? 0.7).toFixed(2)} / {(parsedConfig.rarityWeight ?? 0.3).toFixed(2)} / {(parsedConfig.responsivenessWeight ?? 0).toFixed(2)}
                      </td>
                      <td>{parsedConfig.cooldownDaysMale ?? 90}d / {parsedConfig.cooldownDaysFemale ?? 120}d</td>
                      <td>{parsedConfig.freshnessWindowHours ?? 4}h</td>
                      <td>{h.author || "ADMIN"}</td>
                      <td>{h.createdAt ? new Date(h.createdAt).toLocaleString() : "—"}</td>
                      <td style={{ fontSize: "0.82rem", color: "var(--text-muted)" }}>
                        {h.description || "—"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 3: Audit Logs */}
      {activeTab === "audit" && (
        <div className="card" style={{ padding: "0" }}>
          <div className="table-responsive">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Actor</th>
                  <th>Action</th>
                  <th>Entity</th>
                  <th>Entity ID</th>
                  <th>Details</th>
                </tr>
              </thead>
              <tbody>
                {auditLogs.map((log) => (
                  <tr key={log.id}>
                    <td style={{ whiteSpace: "nowrap", fontSize: "0.82rem" }}>
                      {log.createdAt ? new Date(log.createdAt).toLocaleString() : "—"}
                    </td>
                    <td>
                      <span className="badge badge-neutral" style={{ fontWeight: 600 }}>
                        {log.actorUsername}
                      </span>
                    </td>
                    <td>
                      <span className="badge badge-warning" style={{ fontSize: "0.72rem" }}>
                        {log.action}
                      </span>
                    </td>
                    <td>{log.entityType}</td>
                    <td>{log.entityId || "—"}</td>
                    <td style={{ fontSize: "0.82rem", color: "var(--text-muted)", maxWidth: "320px", overflow: "hidden", textOverflow: "ellipsis" }}>
                      {log.details || "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
