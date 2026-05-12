<!--
  Terms and Conditions page for the DecMed patient client.

  Architecture decisions:
  - IntersectionObserver on a sentinel <div> at the bottom of each section's
    scrollable area to detect scroll-to-bottom.
  - Per-section checkboxes are disabled until the sentinel is observed.
  - Master checkbox disabled until all per-section checkboxes are checked.
  - On wearable viewports (< 320px), sections render as collapsible accordions.
  - Language toggle (EN/ID) with flag indicators in the header. Switching
    language does NOT reset scroll/agreement state — the content structure
    is identical across locales, only text changes.
-->
<script lang="ts">
	import {
		TERMS_SECTIONS,
		TERMS_SECTIONS_ID,
		TERMS_ACCEPTED_KEY,
		TERMS_LOCALE_KEY,
		UI_STRINGS,
		type Locale
	} from '$lib/terms-content';
	import { cn } from '$lib/utils';
	import { goto } from '$app/navigation';
	import {
		ScrollText,
		Hospital,
		Database,
		BellRing,
		ChevronDown,
		ChevronUp,
		ShieldCheck,
		X,
		Languages
	} from '@lucide/svelte';

	// ── Icon mapping ──────────────────────────────────────────────────────
	const iconMap: Record<string, typeof ScrollText> = {
		'scroll-text': ScrollText,
		hospital: Hospital,
		database: Database,
		'bell-ring': BellRing
	};

	// ── Locale state ──────────────────────────────────────────────────────
	// Persisted to localStorage so the preference survives page reloads.
	// Defaults to 'en' if no preference is stored.
	let locale: Locale = $state(
		(typeof window !== 'undefined'
			? (localStorage.getItem(TERMS_LOCALE_KEY) as Locale | null)
			: null) ?? 'en'
	);

	// Derived: select the correct section content array based on locale.
	let sections = $derived(locale === 'id' ? TERMS_SECTIONS_ID : TERMS_SECTIONS);

	// Derived: select the correct UI strings based on locale.
	let t = $derived(UI_STRINGS[locale]);

	function toggleLocale() {
		locale = locale === 'en' ? 'id' : 'en';
		localStorage.setItem(TERMS_LOCALE_KEY, locale);
	}

	// ── Reactive state (Svelte 5 runes) ───────────────────────────────────
	let sectionScrolled: Record<string, boolean> = $state(
		Object.fromEntries(TERMS_SECTIONS.map((s) => [s.id, false]))
	);

	let sectionAgreed: Record<string, boolean> = $state(
		Object.fromEntries(TERMS_SECTIONS.map((s) => [s.id, false]))
	);

	let masterAgreed = $state(false);
	let showDeclineDialog = $state(false);
	let expandedSection: string | null = $state(TERMS_SECTIONS[0]?.id ?? null);

	// ── Derived state ─────────────────────────────────────────────────────
	let allSectionsAgreed = $derived(TERMS_SECTIONS.every((s) => sectionAgreed[s.id]));
	let canProceed = $derived(masterAgreed && allSectionsAgreed);

	// ── IntersectionObserver setup ────────────────────────────────────────
	function observeSentinel(node: HTMLElement, sectionId: string) {
		const observer = new IntersectionObserver(
			(entries) => {
				for (const entry of entries) {
					if (entry.isIntersecting) {
						sectionScrolled[sectionId] = true;
						observer.disconnect();
					}
				}
			},
			{ threshold: 1.0 }
		);
		observer.observe(node);
		return {
			destroy() {
				observer.disconnect();
			}
		};
	}

	// ── Handlers ──────────────────────────────────────────────────────────
	function handleAccept() {
		if (!canProceed) return;
		localStorage.setItem(TERMS_ACCEPTED_KEY, 'true');
		goto('/signin');
	}

	function handleDecline() {
		showDeclineDialog = true;
	}

	function toggleSection(sectionId: string) {
		expandedSection = expandedSection === sectionId ? null : sectionId;
	}
</script>

<svelte:head>
	<title>{t.pageTitle} — DecMed</title>
	<meta
		name="description"
		content="Review and accept the DecMed Terms and Conditions before using the application."
	/>
</svelte:head>

<div
	class="flex min-h-svh w-full flex-col bg-linear-to-br from-zinc-50 via-white to-zinc-100"
	id="terms-page"
>
	<!-- HEADER -->
	<header
		class="sticky top-0 z-20 border-b border-zinc-200 bg-white/80 backdrop-blur-md
		       px-4 py-3 sm:px-6 md:px-8 lg:px-12"
	>
		<div class="mx-auto flex max-w-4xl items-center gap-3">
			<div
				class="flex size-9 items-center justify-center rounded-lg bg-zinc-800
				       text-zinc-100 max-[319px]:size-7 max-[319px]:rounded-md"
			>
				<ShieldCheck class="size-5 max-[319px]:size-4" />
			</div>
			<div class="flex-1">
				<h1
					class="font-montserrat text-lg font-bold leading-tight text-zinc-900
					       max-[319px]:text-sm sm:text-xl"
				>
					{t.pageTitle}
				</h1>
				<p class="text-xs text-zinc-500 max-[319px]:hidden sm:text-sm">
					{t.pageSubtitle}
				</p>
			</div>

			<!-- Language toggle button -->
			<button
				type="button"
				onclick={toggleLocale}
				class="flex items-center gap-2 rounded-lg border border-zinc-200 bg-white
				       px-3 py-1.5 text-sm font-medium text-zinc-700
				       transition-all duration-200 hover:border-zinc-300 hover:bg-zinc-50
				       hover:shadow-sm active:scale-95
				       max-[319px]:gap-1 max-[319px]:px-2 max-[319px]:py-1 max-[319px]:text-xs"
				id="btn-locale-toggle"
				aria-label="Switch language"
			>
				<Languages class="size-4 text-zinc-500 max-[319px]:size-3" />
				{#if locale === 'en'}
					<span class="text-base leading-none max-[319px]:text-sm">🇮🇩</span>
					<span class="max-[319px]:hidden">ID</span>
				{:else}
					<span class="text-base leading-none max-[319px]:text-sm">🇬🇧</span>
					<span class="max-[319px]:hidden">EN</span>
				{/if}
			</button>
		</div>
	</header>

	<!-- INTRO -->
	<div class="mx-auto w-full max-w-4xl px-4 pt-6 sm:px-6 md:px-8 lg:px-12 max-[319px]:px-2 max-[319px]:pt-3">
		<p class="text-sm leading-relaxed text-zinc-600 max-[319px]:text-xs sm:text-base">
			{t.introText}
		</p>
	</div>

	<!-- SECTIONS -->
	<div
		class="mx-auto flex w-full max-w-4xl flex-1 flex-col gap-4 px-4 py-6
		       sm:gap-6 sm:px-6 md:px-8 lg:px-12
		       max-[319px]:gap-2 max-[319px]:px-2 max-[319px]:py-3"
	>
		{#each sections as section, idx (section.id)}
			{@const sectionNumber = idx + 1}
			{@const isScrolled = sectionScrolled[section.id]}
			{@const isAgreed = sectionAgreed[section.id]}
			{@const isExpanded = expandedSection === section.id}
			{@const IconComponent = iconMap[section.icon] ?? ScrollText}

			<section
				class="overflow-hidden rounded-xl border border-zinc-200 bg-white shadow-sm
				       transition-shadow duration-200 hover:shadow-md
				       max-[319px]:rounded-lg"
				id="terms-section-{section.id}"
				aria-labelledby="terms-heading-{section.id}"
			>
				<!-- Section header -->
				<button
					type="button"
					class="flex w-full items-center gap-3 border-b border-zinc-100 bg-zinc-50/80
					       px-4 py-3 text-left
					       max-[319px]:gap-2 max-[319px]:px-2 max-[319px]:py-2
					       sm:px-6 sm:py-4
					       wearable:cursor-default"
					onclick={() => toggleSection(section.id)}
					aria-expanded={isExpanded}
					aria-controls="terms-body-{section.id}"
				>
					<div
						class="flex size-8 shrink-0 items-center justify-center rounded-lg
						       bg-zinc-800 text-zinc-100
						       max-[319px]:size-6 max-[319px]:rounded-md"
					>
						<IconComponent class="size-4 max-[319px]:size-3" />
					</div>
					<div class="flex-1">
						<h2
							id="terms-heading-{section.id}"
							class="text-sm font-semibold text-zinc-900
							       max-[319px]:text-xs sm:text-base"
						>
							{t.sectionPrefix} {sectionNumber}: {section.title}
						</h2>
					</div>
					<!-- Status badge -->
					<div class="flex shrink-0 items-center gap-2">
						{#if isAgreed}
							<span
								class="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-medium
								       text-emerald-700 max-[319px]:text-[10px]"
							>
								{t.badgeAgreed}
							</span>
						{:else if isScrolled}
							<span
								class="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium
								       text-amber-700 max-[319px]:text-[10px]"
							>
								{t.badgeRead}
							</span>
						{:else}
							<span
								class="rounded-full bg-zinc-100 px-2 py-0.5 text-xs font-medium
								       text-zinc-500 max-[319px]:text-[10px]"
							>
								{t.badgeUnread}
							</span>
						{/if}
						<!-- Accordion chevron — wearable only -->
						<span class="text-zinc-400 wearable:hidden">
							{#if isExpanded}
								<ChevronUp class="size-4" />
							{:else}
								<ChevronDown class="size-4" />
							{/if}
						</span>
					</div>
				</button>

				<!-- Section body -->
				<div
					id="terms-body-{section.id}"
					class={cn(
						'flex flex-col',
						'max-[319px]:overflow-hidden max-[319px]:transition-all max-[319px]:duration-300',
						!isExpanded ? 'max-[319px]:max-h-0' : 'max-[319px]:max-h-[2000px]'
					)}
				>
					<div
						class="overflow-y-auto px-4 py-4 sm:px-6
						       max-[319px]:px-2 max-[319px]:py-2
						       max-h-72 sm:max-h-80 md:max-h-96"
					>
						<p
							class="mb-4 text-sm leading-relaxed text-zinc-600
							       max-[319px]:mb-2 max-[319px]:text-xs"
						>
							{section.summary}
						</p>

						<div class="flex flex-col gap-3 max-[319px]:gap-2">
							{#each section.clauses as clause (clause.heading)}
								<div class="flex flex-col gap-1">
									<h3
										class="text-sm font-semibold text-zinc-800
										       max-[319px]:text-xs"
									>
										{clause.heading}
									</h3>
									<p
										class="text-sm leading-relaxed text-zinc-600
										       max-[319px]:text-xs max-[319px]:leading-relaxed"
									>
										{clause.body}
									</p>
								</div>
							{/each}
						</div>

						<!-- Scroll sentinel -->
						<div
							use:observeSentinel={section.id}
							class="h-px w-full"
							aria-hidden="true"
						></div>
					</div>

					<!-- Per-section checkbox -->
					<div
						class="flex items-start gap-3 border-t border-zinc-100 bg-zinc-50/50
						       px-4 py-3 sm:px-6
						       max-[319px]:gap-2 max-[319px]:px-2 max-[319px]:py-2"
					>
						<input
							type="checkbox"
							id="agree-{section.id}"
							disabled={!isScrolled}
							bind:checked={sectionAgreed[section.id]}
							class="mt-0.5 size-4 shrink-0 cursor-pointer rounded border-zinc-300
							       accent-zinc-800
							       disabled:cursor-not-allowed disabled:opacity-40
							       max-[319px]:size-5"
						/>
						<label
							for="agree-{section.id}"
							class={cn(
								'text-sm leading-snug max-[319px]:text-xs',
								isScrolled ? 'text-zinc-700' : 'text-zinc-400'
							)}
						>
							{section.checkboxLabel}
							{#if !isScrolled}
								<span class="ml-1 text-xs italic text-zinc-400 max-[319px]:block">
									{t.scrollHint}
								</span>
							{/if}
						</label>
					</div>
				</div>
			</section>
		{/each}

		<!-- MASTER CONFIRMATION & ACTIONS -->
		<div
			class="mt-2 flex flex-col gap-4 rounded-xl border border-zinc-200 bg-white p-4
			       shadow-sm sm:p-6
			       max-[319px]:mt-1 max-[319px]:gap-2 max-[319px]:rounded-lg max-[319px]:p-2"
			id="terms-master-confirmation"
		>
			<div class="flex items-start gap-3 max-[319px]:gap-2">
				<input
					type="checkbox"
					id="master-agree"
					disabled={!allSectionsAgreed}
					bind:checked={masterAgreed}
					class="mt-0.5 size-4 shrink-0 cursor-pointer rounded border-zinc-300
					       accent-zinc-800
					       disabled:cursor-not-allowed disabled:opacity-40
					       max-[319px]:size-5"
				/>
				<label
					for="master-agree"
					class={cn(
						'text-sm font-medium leading-snug max-[319px]:text-xs',
						allSectionsAgreed ? 'text-zinc-800' : 'text-zinc-400'
					)}
				>
					{t.masterCheckboxLabel}
					{#if !allSectionsAgreed}
						<span class="ml-1 text-xs italic text-zinc-400 max-[319px]:block">
							{t.masterCheckboxHint}
						</span>
					{/if}
				</label>
			</div>

			<div
				class="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end sm:gap-3
				       max-[319px]:gap-1.5"
			>
				<button
					type="button"
					onclick={handleDecline}
					class="rounded-lg border border-zinc-200 bg-white px-5 py-2 text-sm
					       font-medium text-zinc-600 transition-colors duration-150
					       hover:bg-zinc-50
					       max-[319px]:px-3 max-[319px]:py-2 max-[319px]:text-xs"
					id="btn-decline"
				>
					{t.btnDecline}
				</button>
				<button
					type="button"
					disabled={!canProceed}
					onclick={handleAccept}
					class="rounded-lg bg-zinc-800 px-5 py-2 text-sm font-medium text-zinc-100
					       transition-all duration-150
					       hover:bg-zinc-700
					       disabled:cursor-not-allowed disabled:opacity-40
					       max-[319px]:px-3 max-[319px]:py-2 max-[319px]:text-xs"
					id="btn-accept"
				>
					{t.btnAccept}
				</button>
			</div>
		</div>
	</div>

	<!-- FOOTER -->
	<footer
		class="border-t border-zinc-200 bg-white/60 px-4 py-3 text-center text-xs text-zinc-400
		       max-[319px]:py-2 max-[319px]:text-[10px] sm:px-6"
	>
		&copy; {new Date().getFullYear()} {t.footer}
	</footer>
</div>

<!-- DECLINE DIALOG -->
{#if showDeclineDialog}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm
		       p-4 max-[319px]:p-2"
		onkeydown={(e) => { if (e.key === 'Escape') showDeclineDialog = false; }}
	>
		<div
			class="w-full max-w-md rounded-xl border border-zinc-200 bg-white p-6 shadow-xl
			       max-[319px]:max-w-full max-[319px]:rounded-lg max-[319px]:p-3"
			role="alertdialog"
			aria-modal="true"
			aria-labelledby="decline-dialog-title"
			aria-describedby="decline-dialog-desc"
		>
			<div class="mb-4 flex items-center justify-between max-[319px]:mb-2">
				<h2
					id="decline-dialog-title"
					class="text-base font-semibold text-zinc-900 max-[319px]:text-sm"
				>
					{t.dialogTitle}
				</h2>
				<button
					type="button"
					onclick={() => (showDeclineDialog = false)}
					class="rounded-md p-1 text-zinc-400 hover:bg-zinc-100 hover:text-zinc-600"
					aria-label="Close dialog"
				>
					<X class="size-4" />
				</button>
			</div>
			<p
				id="decline-dialog-desc"
				class="mb-6 text-sm leading-relaxed text-zinc-600
				       max-[319px]:mb-3 max-[319px]:text-xs"
			>
				{t.dialogBody1}
			</p>
			<p
				class="mb-6 text-sm leading-relaxed text-zinc-600
				       max-[319px]:mb-3 max-[319px]:text-xs"
			>
				{t.dialogBody2}
			</p>
			<div class="flex justify-end gap-2">
				<button
					type="button"
					onclick={() => (showDeclineDialog = false)}
					class="rounded-lg bg-zinc-800 px-4 py-2 text-sm font-medium text-zinc-100
					       transition-colors duration-150 hover:bg-zinc-700
					       max-[319px]:px-3 max-[319px]:text-xs"
				>
					{t.dialogReturn}
				</button>
			</div>
		</div>
	</div>
{/if}
