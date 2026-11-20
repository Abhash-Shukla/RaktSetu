import React from "react";
import { AlertTriangle } from "lucide-react";

export const ClinicalDisclaimerBanner: React.FC = () => {
  return (
    <div className="clinical-disclaimer-banner" role="alert">
      <AlertTriangle size={16} />
      <span>
        <strong>Clinical Caveat:</strong> The LifeLine system routes and ranks blood donor and inventory candidates based on algorithmic criteria only. Final clinical eligibility, blood typing, cross-matching, and collection decisions belong exclusively to qualified blood-bank staff.
      </span>
    </div>
  );
};
