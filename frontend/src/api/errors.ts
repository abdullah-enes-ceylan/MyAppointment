import axios from "axios";
import type { ErrorResponse, ValidationErrorResponse } from "../types/api";

// Backend hata govdesi iki farkli sekilde gelebiliyor: duz metin (bazi 401'ler)
// ya da yapilandirilmis bir nesne (ErrorResponse/ValidationErrorResponse --
// bkz. GlobalExceptionHandler). catch (err) bloklarinda err'in tipi TS'te
// varsayilan olarak "unknown" (strict mode) -- err.response'a dogrudan
// erismek gercek bir derleme hatasi verir. axios.isAxiosError, err'i
// AxiosError<T>'a daraltan resmi axios type guard'i; bu dosya o daraltmayi
// tek yerden yapip her sayfadaki catch bloklarinin tekrar etmesini onluyor.
type KnownErrorBody = string | ErrorResponse | ValidationErrorResponse;

// Login/Register gibi tek bir genel mesaj yeten sayfalar icin.
export function getErrorMessage(err: unknown, fallback: string): string {
  if (!axios.isAxiosError<KnownErrorBody>(err)) return fallback;
  const data = err.response?.data;
  if (typeof data === "string") return data || fallback;
  if (data && typeof data === "object") return data.message || fallback;
  return fallback;
}

// ProfilePage gibi alan bazli hata gosteren formlar icin -- fieldErrors
// sadece ValidationErrorResponse'ta var (bkz. GlobalExceptionHandler.handleValidation).
export function getValidationErrors(
  err: unknown,
  fallback: string
): { fieldErrors: Record<string, string>; message: string } {
  if (!axios.isAxiosError<KnownErrorBody>(err)) {
    return { fieldErrors: {}, message: fallback };
  }
  const data = err.response?.data;
  if (data && typeof data === "object") {
    const fieldErrors = "fieldErrors" in data ? data.fieldErrors : {};
    return { fieldErrors, message: data.message || fallback };
  }
  if (typeof data === "string" && data) {
    return { fieldErrors: {}, message: data };
  }
  return { fieldErrors: {}, message: fallback };
}
