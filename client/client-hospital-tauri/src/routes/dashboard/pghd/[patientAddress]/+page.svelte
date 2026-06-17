<script lang="ts">
	import type { InvokeGetPghdResponseData, PghdDataPoint } from '$lib/types';
	import { PghdReadState } from './state.svelte.js';

	let { data } = $props();

	let pghdReadState = new PghdReadState({
		accessToken: data.accessToken,
		patientIotaAddress: data.patientIotaAddress
	});

	let selectedPghd = $state<Promise<NonNullable<InvokeGetPghdResponseData>> | null>(null);
	let selectedPghdIndex = $state<number | null>(null);
	let selectedPghdCid = $state<string | null>(null);
	let pendingIntegrityInvalidation = $state<{
		cid: string;
		reason: string;
		detail: string;
	} | null>(null);
	let integrityInvalidationTimer: ReturnType<typeof setTimeout> | null = null;

	const openPghd = (index: number, cid?: string) => {
		pendingIntegrityInvalidation = null;
		if (integrityInvalidationTimer) {
			clearTimeout(integrityInvalidationTimer);
			integrityInvalidationTimer = null;
		}
		selectedPghdIndex = index;
		selectedPghdCid = cid ?? null;
		selectedPghd = pghdReadState
			.getPghd(data.accessToken, index, data.patientIotaAddress)
			.catch((error) => {
				const message = error?.message ?? String(error);
				if (cid && isIntegrityWarning(message)) {
					integrityInvalidationTimer = setTimeout(() => {
						pendingIntegrityInvalidation = {
							cid,
							reason: inferIntegrityFailureReason(message),
							detail: message
						};
						integrityInvalidationTimer = null;
					}, 5000);
				}
				throw error;
			});
	};

	const refreshSelectedPghd = () => {
		if (selectedPghdIndex !== null) {
			openPghd(selectedPghdIndex, selectedPghdCid ?? undefined);
		}
	};

	const confirmIntegrityInvalidation = async () => {
		if (!pendingIntegrityInvalidation) return;
		const invalidation = pendingIntegrityInvalidation;
		const success = await pghdReadState.invalidatePghd(invalidation.cid, invalidation.reason);
		if (success) {
			pendingIntegrityInvalidation = null;
			selectedPghd = null;
			selectedPghdIndex = null;
			selectedPghdCid = null;
		}
	};

	const formatDate = (value?: number | string) => {
		if (!value) return '-';
		const date = typeof value === 'number' ? new Date(value < 10_000_000_000 ? value * 1000 : value) : new Date(value);
		return date.toLocaleDateString('id-ID', {
			year: 'numeric',
			month: 'short',
			day: 'numeric',
			hour: '2-digit',
			minute: '2-digit',
			hourCycle: 'h24'
		});
	};

	const formatPointValue = (point: PghdDataPoint) => {
		if (typeof point.value === 'object') {
			return Object.entries(point.value)
				.map(([key, value]) => `${key}: ${value}`)
				.join(', ');
		}
		return `${point.value} ${point.unit}`.trim();
	};

	const formatUnknown = (value: unknown) =>
		value === undefined || value === null || value === '' ? '-' : String(value);

	const isIntegrityWarning = (message: string) => {
		const lower = message.toLowerCase();
		return (
			lower.includes('integrity warning') ||
			lower.includes('signature_invalid') ||
			lower.includes('plain_hash_mismatch') ||
			lower.includes('outer_hash_mismatch') ||
			lower.includes('legacy_pghd_signature_schema') ||
			lower.includes('err_data_corrupted') ||
			lower.includes('hash') ||
			lower.includes('signature')
		);
	};

	const inferIntegrityFailureReason = (message: string) => {
		if (message.includes('SIGNATURE_INVALID')) return 'SIGNATURE_INVALID';
		if (message.includes('PLAIN_HASH_MISMATCH')) return 'PLAIN_HASH_MISMATCH';
		if (message.includes('OUTER_HASH_MISMATCH') || message.includes('ERR_DATA_CORRUPTED')) {
			return 'OUTER_HASH_MISMATCH';
		}
		if (message.includes('LEGACY_PGHD_SIGNATURE_SCHEMA')) return 'LEGACY_PGHD_SIGNATURE_SCHEMA';
		return 'MANUAL_INTEGRITY_INVALIDATION';
	};
</script>

<div class="flex items-start justify-between gap-4 mb-4">
	<div>
		<h2 class="text-lg font-montserrat font-semibold">Patient Generated Health Data</h2>
		<p class="text-sm text-zinc-500">Patient: {data.patientIotaAddress}</p>
	</div>
	<div class="flex gap-2">
		<button
			type="button"
			class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
			onclick={() => pghdReadState.refreshPghdList()}
		>
			Refresh
		</button>
		<a href="/dashboard" class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm">Back</a>
	</div>
</div>

<section class="border border-zinc-200 rounded-md bg-white">
	<div class="p-4 border-b border-zinc-200">
		<h3 class="font-medium">Available PGHD Batches</h3>
		<p class="text-sm text-zinc-500">Encrypted PGHD batches fetched from IOTA/IPFS and verified locally.</p>
	</div>

	<div class="p-4">
		{#await pghdReadState.fetchPghdList}
			<div class="bg-zinc-50 border border-zinc-200 rounded-md p-4 text-sm text-zinc-500">
				Loading PGHD...
			</div>
		{:then pghdList}
			{#if pghdList.length === 0}
				<div class="bg-zinc-50 border border-zinc-200 rounded-md p-4 text-sm text-zinc-500">
					No active PGHD entries found for this patient.
				</div>
			{:else}
				<div class="grid gap-3">
					{#each pghdList as item}
						<div class="border border-zinc-200 rounded-md p-4 bg-white">
							<div class="flex items-start justify-between gap-4">
								<div class="min-w-0">
									<div class="flex items-center gap-2">
										<p class="font-medium">Batch #{item.index}</p>
										<span class="text-xs px-2 py-0.5 rounded-full bg-amber-100 text-amber-700">
											Pending verification
										</span>
									</div>
									<p class="text-sm text-zinc-500 truncate">CID: {item.cid}</p>
									<p class="text-sm text-zinc-500">Recorded: {formatDate(item.timestamp)}</p>
								</div>
								<div class="flex gap-2 shrink-0">
									<button
										type="button"
										class="bg-zinc-800 text-zinc-100 px-3 py-1.5 rounded-md text-sm"
										onclick={() => openPghd(item.index, item.cid)}
									>
										Open
									</button>
									<button
										type="button"
										class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
										onclick={async () => {
											const success = await pghdReadState.invalidatePghd(
												item.cid,
												'MANUAL_REVIEW_INVALIDATION'
											);
											if (success && selectedPghdIndex === item.index) {
												selectedPghd = null;
												selectedPghdIndex = null;
												selectedPghdCid = null;
											}
										}}
									>
										Invalidate
									</button>
								</div>
							</div>
						</div>
					{/each}
				</div>
			{/if}
		{:catch error}
			<div class="bg-red-50 border border-red-200 rounded-md p-4 text-sm text-red-700">
				Unable to load PGHD entries. {error?.message ?? ''}
			</div>
		{/await}
	</div>
</section>

{#if selectedPghd}
	<section class="mt-6">
		{#await selectedPghd}
			<div class="bg-zinc-50 border border-zinc-200 rounded-md p-4 text-sm text-zinc-500">
				Opening and verifying PGHD...
			</div>
		{:then pghd}
			<div class="border border-zinc-200 rounded-md bg-white">
				<div class="p-4 border-b border-zinc-200 flex items-start justify-between gap-4">
					<div>
						<div class="flex items-center gap-2">
							<h4 class="font-medium">PGHD Batch {pghd.pghd_data.batch_id}</h4>
							<span class="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">
								Verified
							</span>
						</div>
						<p class="text-sm text-zinc-500">
							Period: {formatDate(pghd.pghd_data.batch_period.start_timestamp)} - {formatDate(
								pghd.pghd_data.batch_period.end_timestamp
							)}
						</p>
						<p class="text-xs text-zinc-500 mt-1">
							Batch device: {formatUnknown(pghd.pghd_data.source_device?.['device_manufacturer'])}
							{formatUnknown(pghd.pghd_data.source_device?.['device_model'])} · Platform:
							{formatUnknown(pghd.pghd_data.source_device?.['platform'])} · App:
							{formatUnknown(pghd.pghd_data.source_device?.['app_version'])}
						</p>
					</div>
					<div class="flex gap-2">
						<button
							type="button"
							class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
							onclick={refreshSelectedPghd}
						>
							Refresh
						</button>
						<button
							type="button"
							class="border border-red-300 text-red-700 hover:bg-red-50 px-3 py-1.5 rounded-md text-sm"
							onclick={async () => {
								const success = await pghdReadState.invalidatePghd(
									pghd.cid,
									'MANUAL_REVIEW_INVALIDATION'
								);
								if (success) {
									selectedPghd = null;
									selectedPghdIndex = null;
									selectedPghdCid = null;
								}
							}}
						>
							Invalidate
						</button>
						<button
							type="button"
							class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
							onclick={() => {
								selectedPghd = null;
								selectedPghdIndex = null;
								selectedPghdCid = null;
							}}
						>
							Close
						</button>
					</div>
				</div>

				<div class="grid sm:grid-cols-3 gap-3 p-4 border-b border-zinc-200">
					{#each pghd.pghd_data.data_group as group}
						<div class="bg-zinc-50 border border-zinc-200 rounded-md p-3">
							<p class="text-sm font-medium">{group.measurement_type}</p>
							<p class="text-2xl font-semibold">{group.data_points.length}</p>
							<p class="text-xs text-zinc-500">
								Source: {group.source_label ?? group.source} · Device: {group.device_type}
							</p>
						</div>
					{/each}
				</div>

				<div class="p-4 grid gap-4">
					{#each pghd.pghd_data.data_group as group}
						<div>
							<h5 class="font-medium mb-2">{group.measurement_type}</h5>
							<div class="grid sm:grid-cols-4 gap-2 mb-2 text-xs">
								<div class="bg-zinc-50 border border-zinc-200 rounded-md p-2">
									<p class="text-zinc-500">Source</p>
									<p class="font-medium">{group.source_label ?? group.source}</p>
								</div>
								<div class="bg-zinc-50 border border-zinc-200 rounded-md p-2">
									<p class="text-zinc-500">Device Source</p>
									<p class="font-medium">{group.device_source ?? group.source_package_name ?? '-'}</p>
								</div>
								<div class="bg-zinc-50 border border-zinc-200 rounded-md p-2">
									<p class="text-zinc-500">Device Type</p>
									<p class="font-medium">{group.device_type}</p>
								</div>
								<div class="bg-zinc-50 border border-zinc-200 rounded-md p-2">
									<p class="text-zinc-500">Recording Method</p>
									<p class="font-medium">{group.recording_method ?? '-'}</p>
								</div>
							</div>
							<div class="overflow-hidden border border-zinc-200 rounded-md">
								<table class="w-full text-sm">
									<thead class="bg-zinc-50 text-zinc-500">
										<tr>
											<th class="text-left p-2">Time</th>
											<th class="text-left p-2">Value</th>
											<th class="text-left p-2">Source</th>
											<th class="text-left p-2">Method</th>
										</tr>
									</thead>
									<tbody>
										{#each group.data_points as point}
											<tr class="border-t border-zinc-200">
												<td class="p-2">{formatDate(point.timestamp)}</td>
												<td class="p-2">{formatPointValue(point)}</td>
												<td class="p-2">{group.source_label ?? group.source}</td>
												<td class="p-2">{group.recording_method ?? '-'}</td>
											</tr>
										{/each}
									</tbody>
								</table>
							</div>
						</div>
					{/each}
				</div>
			</div>
		{:catch error}
			<div class="bg-red-50 border border-red-200 rounded-md p-4 text-sm text-red-700 whitespace-pre-wrap">
				<p class="font-semibold">PGHD integrity/access warning</p>
				<p class="mt-1">
					{error?.message ?? 'PGHD could not be opened or failed verification.'}
				</p>
			</div>
		{/await}
	</section>
{/if}

{#if pendingIntegrityInvalidation}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
		<div class="w-full max-w-lg rounded-md border border-red-200 bg-white p-5 shadow-xl">
			<p class="text-base font-semibold text-red-700">
				Apakah Anda ingin menginvalidasi/menghapus data ini?
			</p>
			<p class="mt-2 text-sm text-zinc-700">
				Data PGHD ini sudah terdeteksi rusak atau tidak sesuai integritasnya karena
				<span class="font-semibold">{pendingIntegrityInvalidation.reason}</span>. Data tidak boleh
				digunakan untuk keputusan klinis.
			</p>
			<div class="mt-3 rounded-md border border-red-100 bg-red-50 p-3 text-xs text-red-800 whitespace-pre-wrap">
				CID: {pendingIntegrityInvalidation.cid}
				<br />
				{pendingIntegrityInvalidation.detail}
			</div>
			<div class="mt-5 flex justify-end gap-2">
				<button
					type="button"
					class="rounded-md border border-zinc-300 px-3 py-1.5 text-sm"
					onclick={() => {
						pendingIntegrityInvalidation = null;
					}}
				>
					Nanti saja
				</button>
				<button
					type="button"
					class="rounded-md bg-red-700 px-3 py-1.5 text-sm text-white"
					onclick={confirmIntegrityInvalidation}
				>
					Invalidate PGHD
				</button>
			</div>
		</div>
	</div>
{/if}
