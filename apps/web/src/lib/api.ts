import type {
  ApiError,
  AuthResponse,
  ChargeInput,
  Expense,
  ExpenseInput,
  FineInput,
  GroupColor,
  GroupDetail,
  GroupSummary,
  InvitePreview,
  ReceiptScan,
  ServerConfig,
  SettlementInput,
  Sport,
  User,
} from "@split/core";

export interface TeamInput {
  name: string;
  color?: GroupColor;
  sport: Sport;
  club: string;
  season: string;
}

export type TeamPatch = Partial<TeamInput> & { treasurerMemberId?: string };

const TOKEN_KEY = "split.token";

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

/** A failed API call. `message` is Dutch and can be shown to the user. */
export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }
}

let onUnauthorized: () => void = () => {};

/** Lets the session layer react when the server no longer accepts the token. */
export function setUnauthorizedHandler(handler: () => void): void {
  onUnauthorized = handler;
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const token = tokenStore.get();
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiRequestError(0, "network_error", "Geen verbinding. Controleer je internet en probeer het opnieuw.");
  }
  if (response.status === 204) return undefined as T;
  const data: unknown = await response.json().catch(() => null);
  if (!response.ok) {
    const error = (data as ApiError | null)?.error;
    if (response.status === 401 && token) onUnauthorized();
    throw new ApiRequestError(
      response.status,
      error?.code ?? "unknown_error",
      error?.message ?? "Er ging iets mis. Probeer het opnieuw.",
    );
  }
  return data as T;
}

export const api = {
  config: () => request<ServerConfig>("GET", "/config"),

  register: (input: { name: string; email: string; password: string }) =>
    request<AuthResponse>("POST", "/auth/register", input),
  login: (input: { email: string; password: string }) => request<AuthResponse>("POST", "/auth/login", input),
  logout: () => request<void>("POST", "/auth/logout"),

  me: () => request<User>("GET", "/me"),
  updateMe: (input: { name?: string; iban?: string | null }) => request<User>("PATCH", "/me", input),
  deleteMe: () => request<void>("DELETE", "/me"),

  groups: () => request<{ groups: GroupSummary[] }>("GET", "/groups").then((r) => r.groups),
  createGroup: (input: TeamInput) => request<GroupDetail>("POST", "/groups", input),
  group: (groupId: string) => request<GroupDetail>("GET", `/groups/${groupId}`),
  updateGroup: (groupId: string, input: TeamPatch) => request<GroupDetail>("PATCH", `/groups/${groupId}`, input),

  addFine: (groupId: string, input: FineInput) => request<GroupDetail>("POST", `/groups/${groupId}/fines`, input),
  updateFine: (groupId: string, fineId: string, input: FineInput) =>
    request<GroupDetail>("PUT", `/groups/${groupId}/fines/${fineId}`, input),
  removeFine: (groupId: string, fineId: string) => request<GroupDetail>("DELETE", `/groups/${groupId}/fines/${fineId}`),
  leaveGroup: (groupId: string) => request<void>("POST", `/groups/${groupId}/leave`),

  addMember: (groupId: string, name: string) => request<GroupDetail>("POST", `/groups/${groupId}/members`, { name }),
  renameMember: (groupId: string, memberId: string, name: string) =>
    request<GroupDetail>("PATCH", `/groups/${groupId}/members/${memberId}`, { name }),
  removeMember: (groupId: string, memberId: string) =>
    request<GroupDetail>("DELETE", `/groups/${groupId}/members/${memberId}`),

  invite: (code: string) => request<InvitePreview>("GET", `/invites/${encodeURIComponent(code)}`),
  join: (code: string, memberId?: string) =>
    request<GroupDetail>("POST", `/invites/${encodeURIComponent(code)}/join`, memberId ? { memberId } : {}),

  expenses: (groupId: string) =>
    request<{ expenses: Expense[] }>("GET", `/groups/${groupId}/expenses`).then((r) => r.expenses),
  createExpense: (groupId: string, input: ExpenseInput) =>
    request<Expense>("POST", `/groups/${groupId}/expenses`, input),
  updateExpense: (groupId: string, expenseId: string, input: ExpenseInput) =>
    request<Expense>("PUT", `/groups/${groupId}/expenses/${expenseId}`, input),
  deleteExpense: (groupId: string, expenseId: string) =>
    request<void>("DELETE", `/groups/${groupId}/expenses/${expenseId}`),
  createCharge: (groupId: string, input: ChargeInput) => request<Expense>("POST", `/groups/${groupId}/charges`, input),
  createSettlement: (groupId: string, input: SettlementInput) =>
    request<Expense>("POST", `/groups/${groupId}/settlements`, input),

  scanReceipt: (imageBase64: string, mediaType: string) =>
    request<ReceiptScan>("POST", "/receipts/scan", { imageBase64, mediaType }),
};

export const errorMessage = (error: unknown): string =>
  error instanceof Error ? error.message : "Er ging iets mis. Probeer het opnieuw.";
