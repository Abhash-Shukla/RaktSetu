import React from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { HeartHandshake, User, Shield, ClipboardList, LogOut, LogIn, FileText, Activity } from "lucide-react";

export const Header: React.FC = () => {
  const { user, role, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const isActive = (path: string) => location.pathname === path;

  return (
    <header className="main-header">
      <div className="brand-section">
        <Link to="/" style={{ display: "flex", alignItems: "center", gap: "10px" }}>
          <div className="brand-logo">
            <HeartHandshake size={20} />
          </div>
          <div>
            <div className="brand-name">LifeLine <span style={{ color: "var(--primary)", fontSize: "0.85em" }}>/ RaktSetu</span></div>
            <div className="brand-subtitle">Explainable Blood Donor Platform</div>
          </div>
        </Link>
      </div>

      <nav className="nav-links">
        <Link to="/" className={`nav-btn ${isActive("/") ? "active" : ""}`}>
          <FileText size={16} /> Public Intake
        </Link>

        {role === "BANK_STAFF" && (
          <>
            <Link to="/staff/review" className={`nav-btn ${isActive("/staff/review") ? "active" : ""}`}>
              <ClipboardList size={16} /> Review Queue
            </Link>
            <Link to="/staff/requests" className={`nav-btn ${isActive("/staff/requests") ? "active" : ""}`}>
              <Activity size={16} /> Active Requests
            </Link>
          </>
        )}

        {role === "DONOR" && (
          <Link to="/donor" className={`nav-btn ${isActive("/donor") ? "active" : ""}`}>
            <User size={16} /> Donor Portal
          </Link>
        )}

        {role === "ADMIN" && (
          <Link to="/admin" className={`nav-btn ${isActive("/admin") ? "active" : ""}`}>
            <Shield size={16} /> Admin Console
          </Link>
        )}

        {isAuthenticated && user ? (
          <div style={{ display: "flex", alignItems: "center", gap: "10px", marginLeft: "12px" }}>
            <div className="user-badge">
              <span>{user.username}</span>
              <span className="role-tag">{role}</span>
            </div>
            <button onClick={handleLogout} className="btn btn-secondary" style={{ padding: "6px 12px", fontSize: "0.82rem" }} title="Sign Out">
              <LogOut size={14} /> Logout
            </button>
          </div>
        ) : (
          <Link to="/login" className="btn btn-primary" style={{ padding: "7px 16px", marginLeft: "12px" }}>
            <LogIn size={15} /> Sign In
          </Link>
        )}
      </nav>
    </header>
  );
};
