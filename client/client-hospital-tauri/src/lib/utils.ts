import { invoke } from '@tauri-apps/api/core';
import { type ClassValue, clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import type { TryCatchAsValReturn } from './types';

export function cn(...inputs: ClassValue[]) {
	return twMerge(clsx(inputs));
}

export async function copyToClipboard(str: string) {
	navigator.clipboard.writeText(str);
}

export async function reset() {
	await invoke('reset');
}

export function sanitizeInputText(value: string, maxLength: number) {
	return value
		.normalize('NFKC')
		.replace(/[\u0000-\u001F\u007F]/g, ' ')
		.replace(/\s+/g, ' ')
		.trim()
		.slice(0, maxLength);
}

export function sanitizeIdentifier(value: string, maxLength: number) {
	return sanitizeInputText(value, maxLength)
		.replace(/[^a-zA-Z0-9_.:@-]/g, '')
		.replace(/[_.:@-]+/g, (match) => match[0])
		.replace(/^[_.:@-]+|[_.:@-]+$/g, '')
		.slice(0, maxLength);
}

export function sanitizeClinicalText(value: string) {
	return sanitizeInputText(value, 1000);
}

export async function tryCatchAsVal<T>(func: () => Promise<T>): Promise<TryCatchAsValReturn<T>> {
	try {
		const result = await func();
		return { success: true, data: result };
	} catch (e) {
		return { success: false, error: e as string };
	}
}
