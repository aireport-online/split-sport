import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import type { GroupDetail } from "@split/core";
import { api } from "./api";

export const keys = {
  config: ["config"] as const,
  groups: ["groups"] as const,
  group: (groupId: string) => ["groups", groupId] as const,
  expenses: (groupId: string) => ["groups", groupId, "expenses"] as const,
  invite: (code: string) => ["invites", code] as const,
};

export const useConfig = () => useQuery({ queryKey: keys.config, queryFn: api.config, staleTime: Infinity });
export const useGroups = () => useQuery({ queryKey: keys.groups, queryFn: api.groups });
export const useGroup = (groupId: string) => useQuery({ queryKey: keys.group(groupId), queryFn: () => api.group(groupId) });
export const useExpenses = (groupId: string) =>
  useQuery({ queryKey: keys.expenses(groupId), queryFn: () => api.expenses(groupId) });

/** Stores a fresh group returned by a mutation and refreshes the overview. */
export function applyGroup(queryClient: QueryClient, group: GroupDetail): void {
  queryClient.setQueryData(keys.group(group.id), group);
  void queryClient.invalidateQueries({ queryKey: keys.groups, exact: true });
}

/** After an expense or payment changed: balances, the list and the overview are all stale. */
export function useInvalidateGroups(): () => Promise<void> {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: keys.groups });
}

/** A mutation that answers with the updated group. */
export function useGroupMutation<TInput>(mutationFn: (input: TInput) => Promise<GroupDetail>) {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn, onSuccess: (group) => applyGroup(queryClient, group) });
}
