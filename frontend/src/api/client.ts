import type { components, paths } from "./schema";

export type Schema<T extends keyof components["schemas"]> = components["schemas"][T];

export type AuthRequest = Schema<"AuthRequest">;
export type AuthResponse = Schema<"AuthResponse">;
export type RegisterDonorRequest = Schema<"RegisterDonorRequest">;
export type ParseTextRequestDto = Schema<"ParseTextRequestDto">;
export type PublicFreeTextIntakeDto = Schema<"PublicFreeTextIntakeDto">;
export type ParsedIntakeDto = Schema<"ParsedIntakeDto">;
export type CreateBloodRequestDto = Schema<"CreateBloodRequestDto">;
export type ReviewRequestDto = Schema<"ReviewRequestDto">;
export type BloodRequestResponseDto = Schema<"BloodRequestResponseDto">;
export type Tier0CheckDto = Schema<"Tier0CheckDto">;
export type RankedMatchDto = Schema<"RankedMatchDto">;
export type NotificationResponseDto = Schema<"NotificationResponseDto">;
export type DonorProfileDto = Schema<"DonorProfileDto">;
export type UpdateDonorProfileDto = Schema<"UpdateDonorProfileDto">;
export type DonorDecisionDto = Schema<"DonorDecisionDto">;
export type BankInventoryDto = Schema<"BankInventoryDto">;
export type EngineConfigDto = Schema<"EngineConfigDto">;
export type UserSummaryDto = Schema<"UserSummaryDto">;
export type AuditLogDto = Schema<"AuditLogDto">;

const TOKEN_KEY = "lifeline_auth_token";
const USER_KEY = "lifeline_user_profile";

export class ApiClient {
  private baseUrl: string;

  constructor(baseUrl: string = "") {
    this.baseUrl = baseUrl;
  }

  public getToken(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  }

  public setAuth(auth: AuthResponse): void {
    if (auth.token) {
      localStorage.setItem(TOKEN_KEY, auth.token);
      localStorage.setItem(USER_KEY, JSON.stringify(auth));
    }
  }

  public clearAuth(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  public getCurrentUser(): AuthResponse | null {
    try {
      const data = localStorage.getItem(USER_KEY);
      return data ? JSON.parse(data) : null;
    } catch {
      return null;
    }
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const headers = new Headers(options.headers || {});
    headers.set("Content-Type", "application/json");

    const token = this.getToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await fetch(`${this.baseUrl}${endpoint}`, {
      ...options,
      headers,
    });

    if (!response.ok) {
      let errorMessage = `HTTP Error ${response.status}: ${response.statusText}`;
      try {
        const errorJson = await response.json();
        if (errorJson.message) {
          errorMessage = errorJson.message;
        } else if (errorJson.error) {
          errorMessage = errorJson.error;
        }
      } catch {
        // Fallback to default
      }
      throw new Error(errorMessage);
    }

    if (response.status === 204) {
      return {} as T;
    }

    return response.json();
  }

  // --- Auth Endpoints ---
  public login(req: AuthRequest): Promise<AuthResponse> {
    return this.request<AuthResponse>("/api/v1/auth/login", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  public registerDonor(req: RegisterDonorRequest): Promise<AuthResponse> {
    return this.request<AuthResponse>("/api/v1/auth/register-donor", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  // --- Public Intake Endpoints ---
  public submitPublicRequest(req: CreateBloodRequestDto): Promise<BloodRequestResponseDto> {
    return this.request<BloodRequestResponseDto>("/api/v1/public/requests", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  public parsePublicText(req: ParseTextRequestDto): Promise<ParsedIntakeDto> {
    return this.request<ParsedIntakeDto>("/api/v1/public/requests/parse-text", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  public submitPublicFreeText(req: PublicFreeTextIntakeDto): Promise<BloodRequestResponseDto> {
    return this.request<BloodRequestResponseDto>("/api/v1/public/requests/free-text", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  public getPublicRequestStatus(id: number): Promise<BloodRequestResponseDto> {
    return this.request<BloodRequestResponseDto>(`/api/v1/public/requests/${id}/status`, {
      method: "GET",
    });
  }

  // --- Staff Blood Requests ---
  public getPendingRequests(): Promise<BloodRequestResponseDto[]> {
    return this.request<BloodRequestResponseDto[]>("/api/v1/requests/queue/pending-review", {
      method: "GET",
    });
  }

  public getAllRequests(): Promise<BloodRequestResponseDto[]> {
    return this.request<BloodRequestResponseDto[]>("/api/v1/requests", {
      method: "GET",
    });
  }

  public getRequestById(id: number): Promise<BloodRequestResponseDto> {
    return this.request<BloodRequestResponseDto>(`/api/v1/requests/${id}`, {
      method: "GET",
    });
  }

  public createStaffRequest(req: CreateBloodRequestDto): Promise<BloodRequestResponseDto> {
    return this.request<BloodRequestResponseDto>("/api/v1/requests", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  public parseStaffText(req: ParseTextRequestDto): Promise<ParsedIntakeDto> {
    return this.request<ParsedIntakeDto>("/api/v1/requests/parse-text", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  public submitStaffParsedRequest(req: { parsed: ParsedIntakeDto }): Promise<BloodRequestResponseDto> {
    return this.request<BloodRequestResponseDto>("/api/v1/requests/parsed", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  public reviewRequest(id: number, req: ReviewRequestDto): Promise<BloodRequestResponseDto> {
    return this.request<BloodRequestResponseDto>(`/api/v1/requests/${id}/review`, {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  // --- Tier 0 Inventory Check & Fulfill ---
  public checkTier0(id: number): Promise<Tier0CheckDto> {
    return this.request<Tier0CheckDto>(`/api/v1/requests/${id}/tier0-check`, {
      method: "GET",
    });
  }

  public fulfillTier0(id: number, bankId: number): Promise<BloodRequestResponseDto> {
    return this.request<BloodRequestResponseDto>(`/api/v1/requests/${id}/tier0-fulfill/${bankId}`, {
      method: "POST",
    });
  }

  // --- Matching, Ranking & Outreach ---
  public getMatches(id: number, s7: boolean = false): Promise<RankedMatchDto[]> {
    const query = s7 ? "?s7=true" : "";
    return this.request<RankedMatchDto[]>(`/api/v1/requests/${id}/matches${query}`, {
      method: "GET",
    });
  }

  public notifyDonor(requestId: number, donorId: number): Promise<NotificationResponseDto> {
    return this.request<NotificationResponseDto>(`/api/v1/requests/${requestId}/notify-donor/${donorId}`, {
      method: "POST",
    });
  }

  public recordDonorResponse(
    matchLogId: number,
    outcome: "ACCEPTED" | "DECLINED" | "EXPIRED" | "CANCELLED"
  ): Promise<NotificationResponseDto> {
    return this.request<NotificationResponseDto>(
      `/api/v1/requests/match-logs/${matchLogId}/record-response?outcome=${outcome}`,
      {
        method: "POST",
      }
    );
  }

  // --- Donor Self-Service ---
  public getDonorProfile(): Promise<DonorProfileDto> {
    return this.request<DonorProfileDto>("/api/v1/donor/profile", {
      method: "GET",
    });
  }

  public updateDonorProfile(req: UpdateDonorProfileDto): Promise<DonorProfileDto> {
    return this.request<DonorProfileDto>("/api/v1/donor/profile", {
      method: "PUT",
      body: JSON.stringify(req),
    });
  }

  public getDonorNotifications(): Promise<NotificationResponseDto[]> {
    return this.request<NotificationResponseDto[]>("/api/v1/donor/notifications", {
      method: "GET",
    });
  }

  public respondToNotification(matchLogId: number, req: DonorDecisionDto): Promise<NotificationResponseDto> {
    return this.request<NotificationResponseDto>(`/api/v1/donor/notifications/${matchLogId}/respond`, {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  // --- Admin Endpoints ---
  public getAllUsers(): Promise<UserSummaryDto[]> {
    return this.request<UserSummaryDto[]>("/api/v1/admin/users", {
      method: "GET",
    });
  }

  public updateUserStatus(id: number, status: string): Promise<UserSummaryDto> {
    return this.request<UserSummaryDto>(`/api/v1/admin/users/${id}/status?status=${status}`, {
      method: "PATCH",
    });
  }

  public updateUserRole(id: number, role: "DONOR" | "BANK_STAFF" | "ADMIN"): Promise<UserSummaryDto> {
    return this.request<UserSummaryDto>(`/api/v1/admin/users/${id}/role?role=${role}`, {
      method: "PATCH",
    });
  }

  public getActiveConfig(): Promise<EngineConfigDto> {
    return this.request<EngineConfigDto>("/api/v1/admin/config/active", {
      method: "GET",
    });
  }

  public getConfigHistory(): Promise<EngineConfigDto[]> {
    return this.request<EngineConfigDto[]>("/api/v1/admin/config", {
      method: "GET",
    });
  }

  public saveConfig(req: EngineConfigDto): Promise<EngineConfigDto> {
    return this.request<EngineConfigDto>("/api/v1/admin/config", {
      method: "POST",
      body: JSON.stringify(req),
    });
  }

  public getAuditLogs(): Promise<AuditLogDto[]> {
    return this.request<AuditLogDto[]>("/api/v1/admin/audit-logs", {
      method: "GET",
    });
  }

  // --- Inventory Endpoints ---
  public getAllBanks(): Promise<BankInventoryDto[]> {
    return this.request<BankInventoryDto[]>("/api/v1/inventory", {
      method: "GET",
    });
  }
}

export const api = new ApiClient();
