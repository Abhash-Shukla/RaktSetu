import React, { useState } from "react";
import { api, type ParsedIntakeDto, type BloodRequestResponseDto } from "../api/client";
import { FileText, Wand2, CheckCircle2, AlertCircle, Info, ShieldAlert, ArrowRight } from "lucide-react";

export const PublicIntakePage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<"structured" | "freetext">("structured");
  const [loading, setLoading] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successResult, setSuccessResult] = useState<BloodRequestResponseDto | null>(null);

  // Free text input
  const [rawText, setRawText] = useState("");
  const [parsedResult, setParsedResult] = useState<ParsedIntakeDto | null>(null);

  // Form fields
  const [bloodGroup, setBloodGroup] = useState("O_POS");
  const [unitsRequested, setUnitsRequested] = useState(1);
  const [urgency, setUrgency] = useState<"CRITICAL" | "URGENT" | "ROUTINE">("URGENT");
  const [zone, setZone] = useState("Z01");
  const [patientReference, setPatientReference] = useState("");
  const [contactNumber, setContactNumber] = useState("");

  const handleParseText = async () => {
    if (!rawText.trim()) {
      setError("Please paste message or text first");
      return;
    }

    setParsing(true);
    setError(null);
    try {
      const res = await api.parsePublicText({ text: rawText });
      setParsedResult(res);

      // Populate form fields with extracted candidates
      if (res.bloodGroup) setBloodGroup(res.bloodGroup);
      if (res.units && res.units > 0) setUnitsRequested(res.units);
      if (res.urgency) setUrgency(res.urgency as any);
      const extractedZone = (res as any)["zone/pincode"] || (res as any).zone;
      if (extractedZone) setZone(extractedZone);
      if (res.contact) setContactNumber(res.contact);
    } catch (err: any) {
      setError(err.message || "Failed to parse text. Please fill in the structured form below.");
    } finally {
      setParsing(false);
    }
  };

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);

    try {
      const res = await api.submitPublicRequest({
        bloodGroup: bloodGroup as any,
        unitsRequested: Number(unitsRequested),
        urgency: urgency,
        zone: zone.trim(),
        patientReference: patientReference.trim() || "PUBLIC-INTAKE",
      });
      setSuccessResult(res);
    } catch (err: any) {
      setError(err.message || "Submission failed");
    } finally {
      setLoading(false);
    }
  };

  const resetForm = () => {
    setSuccessResult(null);
    setRawText("");
    setParsedResult(null);
    setPatientReference("");
    setContactNumber("");
    setUnitsRequested(1);
  };

  if (successResult) {
    return (
      <div style={{ maxWidth: "640px", margin: "40px auto" }}>
        <div className="card" style={{ textAlign: "center", padding: "40px 32px" }}>
          <div style={{ width: "56px", height: "56px", borderRadius: "50%", background: "var(--success-bg)", color: "var(--success)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 16px auto" }}>
            <CheckCircle2 size={36} />
          </div>
          <h2 style={{ fontSize: "1.4rem", fontWeight: 700, marginBottom: "8px" }}>
            Blood Requirement Submitted
          </h2>
          <div className="badge badge-warning" style={{ fontSize: "0.85rem", padding: "4px 12px", marginBottom: "16px" }}>
            Status: {successResult.status}
          </div>
          <p style={{ color: "var(--text-muted)", fontSize: "0.92rem", marginBottom: "24px", lineHeight: "1.6" }}>
            Your request <strong>#{successResult.id}</strong> has been received and queued in <strong>PENDING_REVIEW</strong>.
            Authorized blood bank staff will verify patient requirements and hospital zone prior to donor outreach.
          </p>

          <div style={{ background: "#f8fafc", border: "1px solid var(--border)", borderRadius: "var(--radius-sm)", padding: "16px", textAlign: "left", marginBottom: "24px" }}>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10px", fontSize: "0.88rem" }}>
              <div><strong>Blood Group:</strong> {successResult.bloodGroup}</div>
              <div><strong>Units Needed:</strong> {successResult.unitsRequested}</div>
              <div><strong>Urgency:</strong> {successResult.urgency}</div>
              <div><strong>Zone:</strong> {successResult.zone}</div>
              {successResult.contactNumber && <div><strong>Contact:</strong> {successResult.contactNumber}</div>}
              {successResult.patientReference && <div><strong>Reference:</strong> {successResult.patientReference}</div>}
            </div>
          </div>

          <button onClick={resetForm} className="btn btn-primary" style={{ margin: "0 auto" }}>
            Submit Another Requirement
          </button>
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: "760px", margin: "0 auto" }}>
      <div style={{ marginBottom: "24px" }}>
        <h1 style={{ fontSize: "1.6rem", fontWeight: 800, color: "var(--text-main)" }}>
          Submit Blood Requirement
        </h1>
        <p style={{ color: "var(--text-muted)", fontSize: "0.92rem", marginTop: "4px" }}>
          Unauthenticated public intake for families and hospital representatives.
        </p>
      </div>

      <div className="alert alert-info" role="note">
        <Info size={20} style={{ flexShrink: 0, marginTop: "2px" }} />
        <div>
          <strong>Staff Review Notice:</strong> Every public submission enters the <strong>PENDING_REVIEW</strong> queue.
          To maintain ethical clinical safeguards, no donors are contacted and matching is not run until qualified blood bank staff inspect and approve the requirement.
        </div>
      </div>

      {error && (
        <div className="alert alert-danger" role="alert">
          <AlertCircle size={20} style={{ flexShrink: 0 }} />
          <div>{error}</div>
        </div>
      )}

      <div className="card">
        <div className="tabs-nav">
          <button
            type="button"
            className={`tab-btn ${activeTab === "structured" ? "active" : ""}`}
            onClick={() => setActiveTab("structured")}
          >
            <FileText size={16} style={{ verticalAlign: "text-bottom", marginRight: "6px" }} />
            Structured Intake Form
          </button>
          <button
            type="button"
            className={`tab-btn ${activeTab === "freetext" ? "active" : ""}`}
            onClick={() => setActiveTab("freetext")}
          >
            <Wand2 size={16} style={{ verticalAlign: "text-bottom", marginRight: "6px" }} />
            Paste Informal Text / Message
          </button>
        </div>

        {activeTab === "freetext" && (
          <div style={{ marginBottom: "24px", paddingBottom: "24px", borderBottom: "1px dashed var(--border)" }}>
            <div className="form-group">
              <label className="form-label">
                Paste Forwarded Message (WhatsApp, Telegram, SMS, or informal note)
              </label>
              <textarea
                className="form-textarea"
                rows={4}
                placeholder="Example: Urgent 2 units O+ve needed at KEM Hospital South Mumbai Z01 for surgery. Contact 9820011223."
                value={rawText}
                onChange={(e) => setRawText(e.target.value)}
              />
              <div className="form-hint">
                Our parser extracts blood type, units, urgency, and location. Raw text is treated strictly as data.
              </div>
            </div>

            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleParseText}
              disabled={parsing || !rawText.trim()}
            >
              <Wand2 size={15} />
              {parsing ? "Parsing text..." : "Extract Fields Automatically"}
            </button>

            {parsedResult && (
              <div style={{ marginTop: "16px", padding: "14px", background: "#f8fafc", borderRadius: "var(--radius-sm)", border: "1px solid var(--border)" }}>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "8px" }}>
                  <div style={{ fontSize: "0.85rem", fontWeight: 700, color: "var(--text-main)" }}>
                    Extraction Confidence: {Math.round((parsedResult.confidence ?? 0) * 100)}%
                  </div>
                  {parsedResult.fallbackRecommended && (
                    <span className="badge badge-warning">
                      <ShieldAlert size={12} /> Review Recommended
                    </span>
                  )}
                </div>

                {parsedResult.flags && parsedResult.flags.length > 0 && (
                  <div style={{ display: "flex", flexWrap: "wrap", gap: "6px", marginBottom: "10px" }}>
                    {parsedResult.flags.map((f, i) => (
                      <span key={i} className="badge badge-neutral" style={{ fontSize: "0.72rem" }}>
                        {f}
                      </span>
                    ))}
                  </div>
                )}

                <div style={{ fontSize: "0.82rem", color: "var(--text-muted)" }}>
                  Extracted fields have been populated in the form below. Please verify and edit them prior to submission.
                </div>
              </div>
            )}
          </div>
        )}

        <form onSubmit={handleFormSubmit}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px" }}>
            <div className="form-group">
              <label className="form-label">Blood Group Needed</label>
              <select
                className="form-select"
                value={bloodGroup}
                onChange={(e) => setBloodGroup(e.target.value)}
                required
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
              <label className="form-label">Units Requested (Whole Blood)</label>
              <input
                type="number"
                min={1}
                max={50}
                className="form-input"
                value={unitsRequested}
                onChange={(e) => setUnitsRequested(parseInt(e.target.value) || 1)}
                required
              />
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px" }}>
            <div className="form-group">
              <label className="form-label">Urgency Level</label>
              <select
                className="form-select"
                value={urgency}
                onChange={(e) => setUrgency(e.target.value as any)}
                required
              >
                <option value="CRITICAL">CRITICAL (Immediate - 1 hour window)</option>
                <option value="URGENT">URGENT (2-4 hours window)</option>
                <option value="ROUTINE">ROUTINE (Scheduled / 24 hours window)</option>
              </select>
              <div className="form-hint">
                * Urgency selects the mathematical weighting profile used during donor ranking.
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Hospital Geographical Zone / PIN</label>
              <input
                type="text"
                className="form-input"
                placeholder="e.g. Z01, Z02, South Mumbai"
                value={zone}
                onChange={(e) => setZone(e.target.value)}
                required
              />
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "16px" }}>
            <div className="form-group">
              <label className="form-label">Patient Reference (Non-identifying)</label>
              <input
                type="text"
                className="form-input"
                placeholder="e.g. Ward-4B Bed-12"
                value={patientReference}
                onChange={(e) => setPatientReference(e.target.value)}
              />
            </div>

            <div className="form-group">
              <label className="form-label">Contact Phone Number (Optional)</label>
              <input
                type="tel"
                className="form-input"
                placeholder="e.g. 9820011223"
                value={contactNumber}
                onChange={(e) => setContactNumber(e.target.value)}
              />
            </div>
          </div>

          <div style={{ marginTop: "16px" }}>
            <button
              type="submit"
              className="btn btn-primary"
              style={{ width: "100%", padding: "12px", fontSize: "1rem" }}
              disabled={loading}
            >
              {loading ? "Submitting Request..." : "Submit to Staff Review Queue"}
              <ArrowRight size={16} />
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
