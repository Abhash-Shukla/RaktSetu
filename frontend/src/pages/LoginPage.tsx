import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { LogIn, UserPlus, Heart, Shield, Stethoscope, AlertCircle } from "lucide-react";

export const LoginPage: React.FC = () => {
  const { login, registerDonor } = useAuth();
  const navigate = useNavigate();

  const [isRegister, setIsRegister] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Login fields
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  // Register fields
  const [email, setEmail] = useState("");
  const [bloodGroup, setBloodGroup] = useState<"A_POS" | "A_NEG" | "B_POS" | "B_NEG" | "AB_POS" | "AB_NEG" | "O_POS" | "O_NEG">("O_POS");
  const [sex, setSex] = useState<"MALE" | "FEMALE">("MALE");
  const [homeZone, setHomeZone] = useState("Z01");

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = await login({ username, password });
      if (res.role === "BANK_STAFF") {
        navigate("/staff/review");
      } else if (res.role === "ADMIN") {
        navigate("/admin");
      } else {
        navigate("/donor");
      }
    } catch (err: any) {
      setError(err.message || "Authentication failed");
    } finally {
      setLoading(false);
    }
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);

    try {
      const res = await registerDonor({
        username,
        email,
        password,
        bloodGroup,
        sex,
        homeZone,
      });
      navigate("/donor");
    } catch (err: any) {
      setError(err.message || "Registration failed");
    } finally {
      setLoading(false);
    }
  };

  const quickFill = (u: string, p: string) => {
    setUsername(u);
    setPassword(p);
    setIsRegister(false);
  };

  return (
    <div style={{ maxWidth: "480px", margin: "40px auto" }}>
      <div className="card">
        <div style={{ textAlign: "center", marginBottom: "24px" }}>
          <div style={{ width: "48px", height: "48px", borderRadius: "12px", background: "var(--primary-light)", color: "var(--primary)", display: "flex", alignItems: "center", justifyContent: "center", margin: "0 auto 12px auto" }}>
            <Heart size={28} />
          </div>
          <h2 style={{ fontSize: "1.5rem", fontWeight: 700 }}>
            {isRegister ? "Join as a Voluntary Donor" : "Sign In to LifeLine"}
          </h2>
          <p style={{ color: "var(--text-muted)", fontSize: "0.88rem", marginTop: "4px" }}>
            {isRegister
              ? "Register to receive urgent blood request notifications in your zone"
              : "Access the explainable blood donor and request dispatch platform"}
          </p>
        </div>

        {error && (
          <div className="alert alert-danger" role="alert">
            <AlertCircle size={18} style={{ flexShrink: 0 }} />
            <div>{error}</div>
          </div>
        )}

        <div className="tabs-nav" style={{ marginBottom: "20px" }}>
          <button
            type="button"
            className={`tab-btn ${!isRegister ? "active" : ""}`}
            style={{ flex: 1, textAlign: "center" }}
            onClick={() => { setIsRegister(false); setError(null); }}
          >
            <LogIn size={15} style={{ verticalAlign: "text-bottom", marginRight: "6px" }} />
            Sign In
          </button>
          <button
            type="button"
            className={`tab-btn ${isRegister ? "active" : ""}`}
            style={{ flex: 1, textAlign: "center" }}
            onClick={() => { setIsRegister(true); setError(null); }}
          >
            <UserPlus size={15} style={{ verticalAlign: "text-bottom", marginRight: "6px" }} />
            Register Donor
          </button>
        </div>

        {!isRegister ? (
          <form onSubmit={handleLoginSubmit}>
            <div className="form-group">
              <label className="form-label">Username</label>
              <input
                type="text"
                className="form-input"
                placeholder="Enter username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <input
                type="password"
                className="form-input"
                placeholder="Enter password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            <button type="submit" className="btn btn-primary" style={{ width: "100%", marginTop: "12px" }} disabled={loading}>
              {loading ? "Authenticating..." : "Sign In"}
            </button>

            <div style={{ marginTop: "24px", paddingTop: "16px", borderTop: "1px dashed var(--border)" }}>
              <div style={{ fontSize: "0.78rem", fontWeight: 600, color: "var(--text-muted)", textTransform: "uppercase", marginBottom: "8px" }}>
                Quick Demo Logins
              </div>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "8px" }}>
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ fontSize: "0.8rem", padding: "6px 8px" }}
                  onClick={() => quickFill("staff_central", "password123")}
                >
                  <Stethoscope size={13} /> Staff
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ fontSize: "0.8rem", padding: "6px 8px" }}
                  onClick={() => quickFill("admin", "admin123")}
                >
                  <Shield size={13} /> Admin
                </button>
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ fontSize: "0.8rem", padding: "6px 8px" }}
                  onClick={() => quickFill("donor_arjun", "SecurePass123!")}
                >
                  <Heart size={13} /> Donor
                </button>
              </div>
            </div>
          </form>
        ) : (
          <form onSubmit={handleRegisterSubmit}>
            <div className="form-group">
              <label className="form-label">Username</label>
              <input
                type="text"
                className="form-input"
                placeholder="Choose username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Email</label>
              <input
                type="email"
                className="form-input"
                placeholder="your.email@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label className="form-label">Password</label>
              <input
                type="password"
                className="form-input"
                placeholder="Choose secure password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "12px" }}>
              <div className="form-group">
                <label className="form-label">Blood Group</label>
                <select
                  className="form-select"
                  value={bloodGroup}
                  onChange={(e) => setBloodGroup(e.target.value as any)}
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
                <label className="form-label">Sex</label>
                <select
                  className="form-select"
                  value={sex}
                  onChange={(e) => setSex(e.target.value as any)}
                  required
                >
                  <option value="MALE">Male (90-day cooldown)</option>
                  <option value="FEMALE">Female (120-day cooldown)</option>
                </select>
              </div>
            </div>
            <div className="form-hint" style={{ marginTop: "-8px", marginBottom: "14px" }}>
              * Sex is used strictly to select statutory cooldown interval (90 days male, 120 days female).
            </div>

            <div className="form-group">
              <label className="form-label">Home Zone</label>
              <input
                type="text"
                className="form-input"
                placeholder="e.g. Z01, Z02, South Mumbai"
                value={homeZone}
                onChange={(e) => setHomeZone(e.target.value)}
                required
              />
            </div>

            <button type="submit" className="btn btn-primary" style={{ width: "100%", marginTop: "12px" }} disabled={loading}>
              {loading ? "Registering..." : "Create Donor Account"}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};
