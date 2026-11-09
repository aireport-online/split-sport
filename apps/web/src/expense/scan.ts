import type { ReceiptScan } from "@split/core";
import { api } from "../lib/api";

const MAX_SIDE = 1600;

/** Scales a photo down to at most 1600 px and re-encodes it as JPEG, which keeps uploads small. */
async function toJpegBase64(file: File): Promise<string> {
  const bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d")!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return canvas.toDataURL("image/jpeg", 0.85).split(",")[1]!;
}

export async function scanReceipt(file: File): Promise<ReceiptScan> {
  let base64: string;
  try {
    base64 = await toJpegBase64(file);
  } catch {
    throw new Error("Deze foto kan niet worden geopend. Kies een JPG of PNG.");
  }
  return api.scanReceipt(base64, "image/jpeg");
}
