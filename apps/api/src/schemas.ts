import { GROUP_COLORS, MAX_AMOUNT_CENTS, MAX_WEIGHT } from "@split/core";
import { z } from "zod";

const trimmed = (max: number) => z.string().trim().min(1).max(max);

const isoDate = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/)
  .refine((value) => {
    const date = new Date(`${value}T00:00:00Z`);
    return !Number.isNaN(date.getTime()) && date.toISOString().slice(0, 10) === value;
  });

const cents = z.number().int().min(0).max(MAX_AMOUNT_CENTS);
const memberId = z.string().min(1).max(64);

/** Accepts an IBAN with or without spaces and stores it compact and uppercase. */
const iban = z
  .string()
  .transform((value) => value.replace(/\s+/g, "").toUpperCase())
  .refine((value) => value === "" || /^[A-Z]{2}\d{2}[A-Z0-9]{10,30}$/.test(value))
  .transform((value) => (value === "" ? null : value));

export const registerSchema = z.object({
  name: trimmed(60),
  email: z.string().trim().toLowerCase().pipe(z.email().max(254)),
  password: z.string().min(8).max(200),
});

export const loginSchema = z.object({
  email: z.string().trim().toLowerCase().min(1).max(254),
  password: z.string().min(1).max(200),
});

export const updateMeSchema = z.object({
  name: trimmed(60).optional(),
  iban: iban.nullable().optional(),
});

export const createGroupSchema = z.object({
  name: trimmed(60),
  color: z.enum(GROUP_COLORS).optional(),
  currency: z
    .string()
    .regex(/^[A-Z]{3}$/)
    .default("EUR"),
});

export const updateGroupSchema = z.object({
  name: trimmed(60).optional(),
  color: z.enum(GROUP_COLORS).optional(),
});

export const memberNameSchema = z.object({ name: trimmed(60) });

export const joinSchema = z.object({ memberId: memberId.optional() });

const splitSchema = z.discriminatedUnion("mode", [
  z.object({ mode: z.literal("equal"), memberIds: z.array(memberId).max(100) }),
  z.object({
    mode: z.literal("exact"),
    amounts: z.array(z.object({ memberId, amountCents: cents })).max(100),
  }),
  z.object({
    mode: z.literal("shares"),
    weights: z.array(z.object({ memberId, weight: z.number().int().min(0).max(MAX_WEIGHT) })).max(100),
  }),
  z.object({
    mode: z.literal("items"),
    items: z
      .array(z.object({ name: z.string().trim().max(120), amountCents: cents, memberIds: z.array(memberId).max(100) }))
      .max(200),
  }),
]);

export const expenseSchema = z.object({
  description: trimmed(120),
  amountCents: cents,
  date: isoDate,
  paidById: memberId,
  split: splitSchema,
});

export const settlementSchema = z.object({
  fromMemberId: memberId,
  toMemberId: memberId,
  amountCents: cents,
  date: isoDate.optional(),
});

export const scanSchema = z.object({
  imageBase64: z.string().min(1),
  mediaType: z.enum(["image/jpeg", "image/png", "image/webp", "image/gif"]),
});
