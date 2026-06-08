import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import type { TryCatchAsValReturn } from './types';

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}

export async function copyToClipboard(str: string) {
	navigator.clipboard.writeText(str);
}

export async function tryCatchAsVal<T>(func: () => Promise<T>): Promise<TryCatchAsValReturn<T>> {
	try {
		const result = await func();
		return { success: true, data: result };
	} catch (e) {
		return { success: false, error: getErrorMessage(e) };
	}
}

export async function waitMs(timeMs: number) {
	await new Promise((resolve) => setTimeout(resolve, timeMs));
}

export function getErrorMessage(error: unknown) {
	if (typeof error === 'string') return error;
	if (error instanceof Error) return error.message;

	if (typeof error === 'object' && error !== null) {
		if ('message' in error && typeof error.message === 'string') {
			return error.message;
		}

		try {
			return JSON.stringify(error);
		} catch {
			return String(error);
		}
	}

	return String(error);
}
