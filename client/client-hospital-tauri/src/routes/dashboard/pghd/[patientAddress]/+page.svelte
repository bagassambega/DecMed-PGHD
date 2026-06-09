<script lang="ts">
	import type { InvokeGetPghdResponseData, PghdDataPoint } from '$lib/types';
	import { PghdReadState } from './state.svelte.js';

	let { data } = $props();

	let pghdReadState = new PghdReadState({
		accessToken: data.accessToken,
		patientIotaAddress: data.patientIotaAddress
	});

	let selectedPghd = $state<Promise<NonNullable<InvokeGetPghdResponseData>> | null>(null);

	const openPghd = (index: number) => {
		selectedPghd = pghdReadState.getPghd(data.accessToken, index, data.patientIotaAddress);
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
</script>

<div class="flex items-start justify-between gap-4 mb-4">
	<div>
		<h2 class="text-lg font-montserrat font-semibold">Patient Generated Health Data</h2>
		<p class="text-sm text-zinc-500">Patient: {data.patientIotaAddress}</p>
	</div>
	<a href="/dashboard" class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm">Back</a>
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
										<span class="text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">
											VALID
										</span>
									</div>
									<p class="text-sm text-zinc-500 truncate">CID: {item.cid}</p>
									<p class="text-sm text-zinc-500">Recorded: {formatDate(item.timestamp)}</p>
								</div>
								<div class="flex gap-2 shrink-0">
									<button
										type="button"
										class="bg-zinc-800 text-zinc-100 px-3 py-1.5 rounded-md text-sm"
										onclick={() => openPghd(item.index)}
									>
										Open
									</button>
									<button
										type="button"
										class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
										onclick={() => pghdReadState.invalidatePghd(item.cid, 'MANUAL_REVIEW_INVALIDATION')}
									>
										Invalidate
									</button>
								</div>
							</div>
						</div>
					{/each}
				</div>
			{/if}
		{:catch}
			<div class="bg-red-50 border border-red-200 rounded-md p-4 text-sm text-red-700">
				Unable to load PGHD entries.
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
					</div>
					<button
						type="button"
						class="border border-zinc-300 px-3 py-1.5 rounded-md text-sm"
						onclick={() => (selectedPghd = null)}
					>
						Close
					</button>
				</div>

				<div class="grid sm:grid-cols-3 gap-3 p-4 border-b border-zinc-200">
					{#each pghd.pghd_data.data_group as group}
						<div class="bg-zinc-50 border border-zinc-200 rounded-md p-3">
							<p class="text-sm font-medium">{group.measurement_type}</p>
							<p class="text-2xl font-semibold">{group.data_points.length}</p>
							<p class="text-xs text-zinc-500">{group.source} / {group.device_type}</p>
						</div>
					{/each}
				</div>

				<div class="p-4 grid gap-4">
					{#each pghd.pghd_data.data_group as group}
						<div>
							<h5 class="font-medium mb-2">{group.measurement_type}</h5>
							<div class="overflow-hidden border border-zinc-200 rounded-md">
								<table class="w-full text-sm">
									<thead class="bg-zinc-50 text-zinc-500">
										<tr>
											<th class="text-left p-2">Time</th>
											<th class="text-left p-2">Value</th>
											<th class="text-left p-2">Method</th>
										</tr>
									</thead>
									<tbody>
										{#each group.data_points as point}
											<tr class="border-t border-zinc-200">
												<td class="p-2">{formatDate(point.timestamp)}</td>
												<td class="p-2">{formatPointValue(point)}</td>
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
		{:catch}
			<div class="bg-red-50 border border-red-200 rounded-md p-4 text-sm text-red-700">
				PGHD could not be opened or failed verification.
			</div>
		{/await}
	</section>
{/if}
