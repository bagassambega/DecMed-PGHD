// Tauri doesn't have a Node.js server to do proper SSR
// so we will use adapter-static to prerender the app (SSG)

import { TERMS_ACCEPTED_KEY } from '$lib/terms-content';
import type { SuccessResponse } from '$lib/types';
import { tryCatchAsVal } from '$lib/utils';
import { invoke } from '@tauri-apps/api/core';
import type { LayoutLoad } from './$types';

// See: https://v2.tauri.app/start/frontend/sveltekit/ for more info
export const prerender = true;
export const ssr = false;

export type LayoutLoadData = {
	redirect_to: string | null;
	terms_accepted: boolean;
};

export const load: LayoutLoad = async ({ url }) => {
	// ── T&C gate ──────────────────────────────────────────────────────
	// Check whether the user has accepted the Terms and Conditions.
	// Uses localStorage for the prototype. To use the Tauri backend
	// instead, replace with: await invoke('check_terms_accepted')
	const termsAccepted =
		typeof window !== 'undefined'
			? localStorage.getItem(TERMS_ACCEPTED_KEY) === 'true'
			: false;

	// If terms are not accepted and the user is not already on the
	// /terms page, short-circuit all other checks and redirect there.
	if (!termsAccepted && url.pathname !== '/terms') {
		return { redirect_to: '/terms', terms_accepted: false };
	}

	// ── Auth status check (existing logic, with Tauri env guard) ─────
	// In a standard browser (non-Tauri), the invoke API is unavailable.
	// We detect the Tauri runtime via the __TAURI_INTERNALS__ global and
	// skip the invoke call entirely when it is absent.
	const isTauri =
		typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;

	const resInvokeAuthState = isTauri
		? await tryCatchAsVal(async () => {
				return (await invoke('auth_status')) as SuccessResponse<null>;
			})
		: ({ success: false, error: 'Not in Tauri environment' } as const);

	const defaultData: LayoutLoadData = {
		redirect_to: null,
		terms_accepted: termsAccepted
	};

	console.log(resInvokeAuthState);

	if (!resInvokeAuthState.success) {
		defaultData.redirect_to = '/signin';
		if (url.pathname === '/signup') {
			defaultData.redirect_to = '/signup';
		}

		// Safely coerce error to string before calling .match(), because
		// caught exceptions may be Error objects rather than raw strings.
		const errorStr = String(resInvokeAuthState.error ?? '');
		const redirect_code = errorStr.match(/\$<(\d+)>\$/);

		if (redirect_code && parseInt(redirect_code[1]) === 1) {
			defaultData.redirect_to = '/complete-profile';
		}
	}

	if (!defaultData.redirect_to && !url.pathname.startsWith('/dashboard')) {
		defaultData.redirect_to = '/dashboard';
	}

	console.log(defaultData);

	return defaultData;
};

