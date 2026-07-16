<script lang="ts">
	import type { InvokeGetPghdResponseData, PghdDataGroup, PghdDataPoint } from '$lib/types';
	import PghdMeasurementVisualization from './PghdMeasurementVisualization.svelte';
	import { PghdReadState } from './state.svelte.js';

	let { data } = $props();

	let pghdReadState = new PghdReadState({
		accessToken: data.accessToken,
		patientIotaAddress: data.patientIotaAddress
	});

	let selectedPghd = $state<Promise<NonNullable<InvokeGetPghdResponseData>> | null>(null);
	let selectedPghdIndex = $state<number | null>(null);
	let selectedPghdCid = $state<string | null>(null);
	let pendingInvalidation = $state<{
		cid: string;
		reason: string;
		detail: string;
	} | null>(null);
	let integrityInvalidationTimer: ReturnType<typeof setTimeout> | null = null;
	let openingIndex = $state<number | null>(null);
	let isRefreshingList = $state(false);
	let isRefreshingSelected = $state(false);
	let invalidatingCid = $state<string | null>(null);
	let selectedMeasurementType = $state('');

	const openPghd = (index: number, cid?: string) => {
		if (openingIndex !== null) return;
		pendingInvalidation = null;
		if (integrityInvalidationTimer) {
			clearTimeout(integrityInvalidationTimer);
			integrityInvalidationTimer = null;
		}
		openingIndex = index;
		selectedPghdIndex = index;
		selectedPghdCid = cid ?? null;
		selectedMeasurementType = '';
		selectedPghd = pghdReadState
			.getPghd(data.accessToken, index, data.patientIotaAddress)
			.catch((error) => {
				const message = error?.message ?? String(error);
				if (cid && isIntegrityWarning(message)) {
					integrityInvalidationTimer = setTimeout(() => {
						pendingInvalidation = {
							cid,
							reason: inferIntegrityFailureReason(message),
							detail: message
						};
						integrityInvalidationTimer = null;
					}, 5000);
				}
				throw error;
			})
			.finally(() => {
				openingIndex = null;
				isRefreshingSelected = false;
			});
	};

	const refreshSelectedPghd = () => {
		if (selectedPghdIndex !== null) {
			isRefreshingSelected = true;
			openPghd(selectedPghdIndex, selectedPghdCid ?? undefined);
		}
	};

	const closeSelectedPghd = () => {
		selectedPghd = null;
		selectedPghdIndex = null;
		selectedPghdCid = null;
		selectedMeasurementType = '';
	};

	const refreshPghdList = async () => {
		if (isRefreshingList) return;
		isRefreshingList = true;
		try {
			await pghdReadState.refreshPghdList();
		} finally {
			isRefreshingList = false;
		}
	};

	const requestInvalidation = (cid: string, reason: string, detail: string) => {
		if (invalidatingCid) return;
		pendingInvalidation = { cid, reason, detail };
	};

	const confirmIntegrityInvalidation = async () => {
		if (!pendingInvalidation || invalidatingCid) return;
		const invalidation = pendingInvalidation;
		invalidatingCid = invalidation.cid;
		try {
			const success = await pghdReadState.invalidatePghd(invalidation.cid, invalidation.reason);
			if (success) {
				pendingInvalidation = null;
				closeSelectedPghd();
			}
		} finally {
			invalidatingCid = null;
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

	const humanize = (value: string) =>
		value.replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());

	const formatNumber = (value: number) =>
		new Intl.NumberFormat('id-ID', { maximumFractionDigits: 2 }).format(value);

	const measurementTypes = (groups: PghdDataGroup[]) => [
		...new Set(groups.map((group) => group.measurement_type))
	];

	const activeMeasurementType = (groups: PghdDataGroup[]) => {
		const available = measurementTypes(groups);
		return available.includes(selectedMeasurementType) ? selectedMeasurementType : (available[0] ?? '');
	};

	const groupsForMeasurement = (groups: PghdDataGroup[]) => {
		const active = activeMeasurementType(groups);
		return groups.filter((group) => group.measurement_type === active);
	};

	const thresholdRange = (threshold: NonNullable<PghdDataGroup['clinical_thresholds']>[number]) => {
		const left = threshold.minimum_inclusive ? '[' : '(';
		const right = threshold.maximum_inclusive ? ']' : ')';
		return `${left}${formatNumber(threshold.minimum)}, ${formatNumber(threshold.maximum)}${right} ${threshold.unit}`;
	};

	const anomalyLabel = (point: PghdDataPoint, group: PghdDataGroup) => {
		if (!point.anomalies?.length) {
			return group.clinical_thresholds?.length
				? 'Dalam rentang normal'
				: 'Rentang klinis tidak tersedia';
		}
		return point.anomalies
			.map(
				(anomaly) =>
					`${humanize(anomaly.field)} ${anomaly.direction === 'below_range' ? 'di bawah' : 'di atas'} rentang normal`
			)
			.join(', ');
	};

	const isIntegrityWarning = (message: string) => {
		const lower = message.toLowerCase();
		return (
			lower.includes('integrity warning') ||
			lower.includes('signature_invalid') ||
			lower.includes('outer_hash_mismatch') ||
			lower.includes('legacy_pghd_signature_schema') ||
			lower.includes('err_data_corrupted') ||
			lower.includes('hash') ||
			lower.includes('signature')
		);
	};

	const inferIntegrityFailureReason = (message: string) => {
		if (message.includes('SIGNATURE_INVALID')) return 'SIGNATURE_INVALID';
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
		<p class="text-sm text-zinc-700">Patient: {data.patientName}</p>
		<p class="text-xs text-zinc-500 break-all">{data.patientIotaAddress}</p>
	</div>
	<div class="flex gap-2">
		<button
			type="button"
			class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
			disabled={isRefreshingList}
			onclick={refreshPghdList}
		>
			{isRefreshingList ? 'Refreshing...' : 'Refresh'}
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
									<p class="text-sm text-zinc-700">Patient: {data.patientName}</p>
									<p class="text-sm text-zinc-500 truncate">CID: {item.cid}</p>
									<p class="text-sm text-zinc-500">Recorded: {formatDate(item.timestamp)}</p>
								</div>
								<div class="flex gap-2 shrink-0">
									<button
										type="button"
										class="bg-zinc-800 text-zinc-100 px-3 py-1.5 rounded-md text-sm"
										disabled={openingIndex !== null || invalidatingCid !== null}
										onclick={() => openPghd(item.index, item.cid)}
									>
										{openingIndex === item.index ? 'Opening...' : 'Open'}
									</button>
									<button
										type="button"
										class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
										disabled={openingIndex !== null || invalidatingCid !== null}
										onclick={() => {
											requestInvalidation(
												item.cid,
												'MANUAL_REVIEW_INVALIDATION',
												`Manual invalidation requested for PGHD batch #${item.index}.`
											);
										}}
									>
										{invalidatingCid === item.cid ? 'Invalidating...' : 'Invalidate'}
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
	<div class="fixed inset-0 z-40 flex items-center justify-center bg-black/40 p-4">
		<button
			type="button"
			class="absolute inset-0 cursor-default"
			aria-label="Close PGHD detail"
			disabled={invalidatingCid !== null}
			onclick={closeSelectedPghd}
		></button>
		{#await selectedPghd}
			<div class="relative w-full max-w-xl rounded-md border border-zinc-200 bg-white p-4 text-sm text-zinc-500 shadow-xl">
				Opening and verifying PGHD...
			</div>
		{:then pghd}
			{@const availableMeasurementTypes = measurementTypes(pghd.pghd_data.data_group)}
			{@const activeType = activeMeasurementType(pghd.pghd_data.data_group)}
			{@const activeGroups = groupsForMeasurement(pghd.pghd_data.data_group)}
			{@const totalAnomalies = activeGroups.reduce((total, group) => total + (group.anomaly_count ?? group.data_points.reduce((count, point) => count + (point.anomalies?.length ?? 0), 0)), 0)}
			{@const thresholds = activeGroups.flatMap((group) => group.clinical_thresholds ?? []).filter((threshold, index, all) => all.findIndex((candidate) => candidate.field === threshold.field && candidate.reference_url === threshold.reference_url) === index)}
			{@const statisticsCount = activeGroups.reduce((total, group) => total + (group.statistics?.length ?? 0), 0)}
			{@const hasConfiguredThresholds = thresholds.length > 0}
			<div id="pghd-detail-modal" class="relative max-h-[90vh] w-full max-w-[1600px] overflow-y-auto rounded-md border border-zinc-200 bg-white shadow-xl">
				<div class="p-4 border-b border-zinc-200 flex items-start justify-between gap-4">
					<div>
						<div class="flex items-center gap-2">
							<h4 class="font-medium">
								PGHD Batch {selectedPghdIndex !== null ? `#${selectedPghdIndex}` : ''}
							</h4>
							<span class="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">
								Verified
							</span>
						</div>
						<p class="text-sm text-zinc-500">
							Batch ID: {pghd.pghd_data.batch_id}
						</p>
						<p class="text-sm text-zinc-500">
							Collection window: {formatDate(pghd.pghd_data.collection_period?.started_at)} - {formatDate(
								pghd.pghd_data.collection_period?.ended_at
							)}
						</p>
						<p class="text-sm text-zinc-500">
							Data collected period: {formatDate(pghd.pghd_data.batch_period.start_timestamp)} - {formatDate(
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
							disabled={isRefreshingSelected || invalidatingCid !== null}
							onclick={refreshSelectedPghd}
						>
							{isRefreshingSelected ? 'Refreshing...' : 'Refresh'}
						</button>
						<button
							type="button"
							class="border border-red-300 text-red-700 hover:bg-red-50 px-3 py-1.5 rounded-md text-sm"
							disabled={isRefreshingSelected || invalidatingCid !== null}
							onclick={() => {
								requestInvalidation(
									pghd.cid,
									'MANUAL_REVIEW_INVALIDATION',
									`Manual invalidation requested for PGHD batch ${pghd.pghd_data.batch_id}.`
								);
							}}
						>
							{invalidatingCid === pghd.cid ? 'Invalidating...' : 'Invalidate'}
						</button>
						<button
							type="button"
							class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
							disabled={invalidatingCid !== null}
							onclick={closeSelectedPghd}
						>
							Close
						</button>
					</div>
				</div>

				<div class="border-b border-zinc-200 px-4 pt-4">
					<p class="mb-2 text-xs font-medium uppercase tracking-wide text-zinc-500">Tipe data</p>
					<div class="flex gap-2 overflow-x-auto pb-3">
						{#each availableMeasurementTypes as measurementType}
							<button
								type="button"
								class={`whitespace-nowrap rounded-md border px-3 py-2 text-sm ${activeType === measurementType ? 'border-zinc-800 bg-zinc-800 text-white' : 'border-zinc-300 bg-white text-zinc-700'}`}
								onclick={() => (selectedMeasurementType = measurementType)}
							>
								{humanize(measurementType)}
							</button>
						{/each}
					</div>
				</div>

				<div class="grid gap-4 p-4">
					<div class={`rounded-md border p-4 ${totalAnomalies > 0 ? 'border-red-200 bg-red-50' : hasConfiguredThresholds ? 'border-green-200 bg-green-50' : 'border-zinc-200 bg-zinc-50'}`}>
						<div class="flex items-start justify-between gap-4">
							<div>
								<h5 class={`font-medium ${totalAnomalies > 0 ? 'text-red-800' : hasConfiguredThresholds ? 'text-green-800' : 'text-zinc-700'}`}>
									{totalAnomalies > 0
										? `${totalAnomalies} penanda anomali ditemukan`
										: hasConfiguredThresholds
											? 'Tidak ada anomali berdasarkan rentang yang dikonfigurasi'
											: 'Rentang klinis tidak tersedia untuk tipe data ini'}
								</h5>
							</div>
						</div>
					</div>

					<section>
						<h5 class="mb-2 font-medium">Ringkasan statistik</h5>
						{#if activeGroups.some((group) => group.statistics?.length)}
							<div class={`grid gap-3 ${statisticsCount > 1 ? 'lg:grid-cols-2' : 'grid-cols-1'}`}>
								{#each activeGroups as group}
									{#each group.statistics ?? [] as summary}
										<div class="rounded-md border border-zinc-200 bg-zinc-50 p-3">
											<div class="mb-3 flex items-start justify-between gap-2">
												<div>
													<p class="font-medium">{summary.field === 'value' ? humanize(activeType) : humanize(summary.field)}</p>
													<p class="text-xs text-zinc-500">{summary.count} data · {group.source_label ?? group.source} · {summary.unit}</p>
												</div>
											</div>
											<div class="grid grid-cols-3 gap-2 text-xs sm:grid-cols-6">
												<div><p class="text-zinc-500">Minimum</p><p class="font-semibold">{formatNumber(summary.minimum)}</p></div>
												<div><p class="text-zinc-500">Maksimum</p><p class="font-semibold">{formatNumber(summary.maximum)}</p></div>
												<div><p class="text-zinc-500">Rata-rata</p><p class="font-semibold">{formatNumber(summary.mean)}</p></div>
												<div><p class="text-zinc-500">Median</p><p class="font-semibold">{formatNumber(summary.median)}</p></div>
												<div class="col-span-2"><p class="text-zinc-500">Modus</p><p class="font-semibold">{summary.mode.length ? summary.mode.map(formatNumber).join(', ') : 'Tidak ada'}</p></div>
											</div>
											<div class="mt-3 grid grid-cols-5 gap-2 border-t border-zinc-200 pt-2 text-xs">
												{#each [['P5', summary.percentiles.p5], ['P25', summary.percentiles.p25], ['P50', summary.percentiles.p50], ['P75', summary.percentiles.p75], ['P95', summary.percentiles.p95]] as percentile}
													<div><p class="text-zinc-500">{percentile[0]}</p><p class="font-medium">{formatNumber(Number(percentile[1]))}</p></div>
												{/each}
											</div>
										</div>
									{/each}
								{/each}
							</div>
						{:else}
							<div class="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">Batch lama tidak membawa ringkasan statistik. Data mentah tetap tersedia di bawah.</div>
						{/if}
					</section>

					<section>
						<h5 class="mb-2 font-medium">Rentang klinis yang digunakan saat pembentukan batch</h5>
						{#if thresholds.length}
							<div class="grid gap-3 md:grid-cols-2">
								{#each thresholds as threshold}
									<div class="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm">
										<p class="font-medium text-amber-900">{threshold.label}</p>
										<p class="mt-1 text-lg font-semibold text-zinc-900">{thresholdRange(threshold)}</p>
										<p class="mt-1 text-xs text-zinc-600">Populasi/konteks: {threshold.population}</p>
										<a class="mt-2 inline-block text-xs text-blue-700 underline" href={threshold.reference_url} target="_blank" rel="noreferrer">{threshold.reference}</a>
									</div>
								{/each}
							</div>
						{:else}
							<div class="rounded-md border border-zinc-200 bg-zinc-50 p-3 text-sm text-zinc-600">Tidak ada rentang klinis umum yang dikonfigurasi untuk tipe data ini. Nilai ditampilkan tanpa klasifikasi normal/anomali.</div>
						{/if}
					</section>

					<PghdMeasurementVisualization groups={activeGroups} measurementType={activeType} />

					<section>
						<h5 class="mb-2 font-medium">Detail seluruh data PGHD</h5>
						<div class="grid gap-4">
							{#each activeGroups as group}
								<div class="overflow-hidden rounded-md border border-zinc-200">
									<div class="grid gap-2 bg-zinc-50 p-3 text-xs sm:grid-cols-4">
										<div><p class="text-zinc-500">Sumber</p><p class="font-medium">{group.source_label ?? group.source}</p></div>
										<div><p class="text-zinc-500">Sumber perangkat</p><p class="font-medium">{group.device_source ?? group.source_package_name ?? '-'}</p></div>
										<div><p class="text-zinc-500">Tipe perangkat</p><p class="font-medium">{group.device_type}</p></div>
										<div><p class="text-zinc-500">Metode</p><p class="font-medium">{group.recording_method ?? '-'}</p></div>
									</div>
									<div class="overflow-x-auto">
										<table class="w-full min-w-[760px] text-sm">
											<thead class="border-t border-zinc-200 bg-zinc-50 text-zinc-500">
												<tr><th class="p-2 text-left">Waktu</th><th class="p-2 text-left">Nilai asli</th><th class="p-2 text-left">Status</th><th class="p-2 text-left">Sumber</th><th class="p-2 text-left">Metode</th></tr>
											</thead>
											<tbody>
												{#each group.data_points as point}
													<tr class={`border-t border-zinc-200 ${point.anomalies?.length ? 'bg-red-50 text-red-900' : ''}`}>
														<td class="p-2">{formatDate(point.timestamp)}</td>
														<td class="p-2 font-medium">{formatPointValue(point)}</td>
														<td class="p-2"><span class={`rounded-full px-2 py-1 text-xs ${point.anomalies?.length ? 'bg-red-100 text-red-800' : group.clinical_thresholds?.length ? 'bg-green-100 text-green-800' : 'bg-zinc-100 text-zinc-700'}`}>{anomalyLabel(point, group)}</span></td>
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
					</section>
				</div>
			</div>
		{:catch error}
			<div class="relative w-full max-w-2xl rounded-md border border-red-200 bg-red-50 p-4 text-sm text-red-700 whitespace-pre-wrap shadow-xl">
				<p class="font-semibold">PGHD integrity/access warning</p>
				<p class="mt-1">
					{error?.message ?? 'PGHD could not be opened or failed verification.'}
				</p>
				<div class="mt-4 flex justify-end">
					<button
						type="button"
						class="rounded-md border border-red-300 bg-white px-3 py-1.5 text-sm text-red-700"
						onclick={closeSelectedPghd}
					>
						Close
					</button>
				</div>
			</div>
		{/await}
	</div>
{/if}

{#if pendingInvalidation}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
		<div class="w-full max-w-lg rounded-md border border-red-200 bg-white p-5 shadow-xl">
			<p class="text-base font-semibold text-red-700">
				Apakah Anda ingin menginvalidasi/menghapus data ini?
			</p>
			<p class="mt-2 text-sm text-zinc-700">
				Anda akan menandai entri PGHD ini sebagai invalid dengan alasan
				<span class="font-semibold">{pendingInvalidation.reason}</span>. Setelah invalidasi berhasil,
				entri ini tidak akan ditampilkan sebagai data valid dan tidak boleh digunakan untuk keputusan klinis.
			</p>
			<div class="mt-3 rounded-md border border-red-100 bg-red-50 p-3 text-xs text-red-800 whitespace-pre-wrap">
				CID: {pendingInvalidation.cid}
				<br />
				{pendingInvalidation.detail}
			</div>
			<div class="mt-5 flex justify-end gap-2">
				<button
					type="button"
					class="rounded-md border border-zinc-300 px-3 py-1.5 text-sm"
					disabled={invalidatingCid !== null}
					onclick={() => {
						pendingInvalidation = null;
					}}
				>
					Nanti saja
				</button>
				<button
					type="button"
					class="rounded-md bg-red-700 px-3 py-1.5 text-sm text-white"
					disabled={invalidatingCid !== null}
					onclick={confirmIntegrityInvalidation}
				>
					{invalidatingCid ? 'Invalidating...' : 'Invalidate PGHD'}
				</button>
			</div>
		</div>
	</div>
{/if}
