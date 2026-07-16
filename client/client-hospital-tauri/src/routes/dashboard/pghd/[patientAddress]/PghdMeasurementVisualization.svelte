<script lang="ts">
	import type { PghdClinicalThreshold, PghdDataGroup } from '$lib/types';

	let { groups, measurementType }: { groups: PghdDataGroup[]; measurementType: string } = $props();

	type PlotPoint = { timestamp: number; value: number; anomaly: boolean };
	type PlotSeries = {
		field: string;
		label: string;
		unit: string;
		color: string;
		points: PlotPoint[];
		threshold?: PghdClinicalThreshold;
	};
	type ChartKind = 'line' | 'bar' | 'box' | 'none';

	const colors = ['#2563eb', '#7c3aed', '#0891b2', '#059669', '#d97706'];
	const barTypes = new Set([
		'steps',
		'distance',
		'active_calories_burned',
		'total_calories_burned',
		'floors_climbed',
		'elevation_gained',
		'sleep_duration'
	]);
	const lineTypes = new Set([
		'heart_rate',
		'resting_heart_rate',
		'oxygen_saturation',
		'respiratory_rate',
		'body_temperature',
		'basal_body_temperature',
		'blood_pressure',
		'blood_glucose',
		'heart_rate_variability',
		'skin_temperature'
	]);

	const series = $derived.by(() => buildSeries(groups));
	const chartKind: ChartKind = $derived.by(() => {
		if (series.length === 0) return 'none';
		if (barTypes.has(measurementType)) return 'bar';
		if (lineTypes.has(measurementType)) return 'line';
		return series.some((item) => item.points.length >= 5) ? 'box' : 'line';
	});
	const allValues = $derived(series.flatMap((item) => item.points.map((point) => point.value)));
	const allTimes = $derived(series.flatMap((item) => item.points.map((point) => point.timestamp)));
	const yMinimum = $derived(Math.min(...allValues, ...series.flatMap(thresholdBounds), ...(chartKind === 'bar' ? [0] : [])));
	const yMaximum = $derived(Math.max(...allValues, ...series.flatMap(thresholdBounds), ...(chartKind === 'bar' ? [0] : [])));
	const xMinimum = $derived(Math.min(...allTimes));
	const xMaximum = $derived(Math.max(...allTimes));

	function buildSeries(inputGroups: PghdDataGroup[]): PlotSeries[] {
		const collected = new Map<string, PlotSeries>();
		for (const group of inputGroups) {
			for (const point of group.data_points) {
				const fields = numericFields(point.value);
				for (const [field, value] of fields) {
					const key = `${field}|${point.unit}`;
					let item = collected.get(key);
					if (!item) {
						const threshold = group.clinical_thresholds?.find((candidate) => candidate.field === field);
						item = {
							field,
							label: field === 'value' ? humanize(measurementType) : humanize(field),
							unit: point.unit,
							color: colors[collected.size % colors.length],
							points: [],
							threshold
						};
						collected.set(key, item);
					}
					item.points.push({
						timestamp: point.timestamp,
						value,
						anomaly: point.anomalies?.some((anomaly) => anomaly.field === field) ?? false
					});
				}
			}
		}
		return [...collected.values()].map((item) => ({
			...item,
			points: item.points.sort((left, right) => left.timestamp - right.timestamp)
		}));
	}

	function numericFields(value: unknown): [string, number][] {
		if (typeof value === 'number' && Number.isFinite(value)) return [['value', value]];
		if (!value || typeof value !== 'object') return [];
		return Object.entries(value)
			.filter((entry): entry is [string, number] => typeof entry[1] === 'number' && Number.isFinite(entry[1]));
	}

	function thresholdBounds(item: PlotSeries): number[] {
		return item.threshold ? [item.threshold.minimum, item.threshold.maximum] : [];
	}

	function humanize(value: string): string {
		return value.replaceAll('_', ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
	}

	function xPosition(timestamp: number): number {
		if (xMaximum === xMinimum) return 450;
		return 58 + ((timestamp - xMinimum) / (xMaximum - xMinimum)) * 812;
	}

	function yPosition(value: number): number {
		if (yMaximum === yMinimum) return 135;
		return 250 - ((value - yMinimum) / (yMaximum - yMinimum)) * 210;
	}

	function linePath(item: PlotSeries): string {
		return item.points
			.map((point, index) => `${index === 0 ? 'M' : 'L'} ${xPosition(point.timestamp)} ${yPosition(point.value)}`)
			.join(' ');
	}

	function percentile(values: number[], percent: number): number {
		const sorted = [...values].sort((a, b) => a - b);
		if (sorted.length === 1) return sorted[0];
		const rank = (percent / 100) * (sorted.length - 1);
		const lower = Math.floor(rank);
		const upper = Math.ceil(rank);
		return sorted[lower] + (sorted[upper] - sorted[lower]) * (rank - lower);
	}

	function formatNumber(value: number): string {
		return new Intl.NumberFormat('id-ID', { maximumFractionDigits: 2 }).format(value);
	}
</script>

<section class="rounded-md border border-zinc-200 bg-white p-4">
	<div class="mb-3 flex flex-wrap items-center justify-between gap-2">
		<div>
			<h6 class="font-medium">Visualisasi {humanize(measurementType)}</h6>
			<p class="text-xs text-zinc-500">
				{chartKind === 'line' ? 'Grafik garis untuk tren waktu' : chartKind === 'bar' ? 'Grafik batang untuk nilai periodik' : chartKind === 'box' ? 'Box plot untuk distribusi data' : 'Tidak ada data numerik yang dapat divisualisasikan'}
			</p>
		</div>
		<div class="flex flex-wrap gap-3 text-xs">
			{#each series as item}
				<span class="flex items-center gap-1"><span class="h-2.5 w-2.5 rounded-full" style:background={item.color}></span>{item.label}</span>
			{/each}
			<span class="flex items-center gap-1"><span class="h-2.5 w-2.5 rounded-full bg-red-600"></span>Anomali</span>
		</div>
	</div>

	{#if chartKind === 'none'}
		<div class="rounded-md bg-zinc-50 p-6 text-center text-sm text-zinc-500">Data bertipe kategorikal atau tidak memiliki nilai numerik.</div>
	{:else}
		<div class="overflow-x-auto">
			<svg viewBox="0 0 900 285" class="min-w-[680px] w-full" role="img" aria-label={`Visualisasi ${measurementType}`}>
				<rect x="58" y="40" width="812" height="210" fill="#fafafa" stroke="#e4e4e7" />
				{#each [0, 1, 2, 3, 4] as tick}
					{@const tickValue = yMaximum - ((yMaximum - yMinimum) * tick) / 4}
					<line x1="58" x2="870" y1={40 + tick * 52.5} y2={40 + tick * 52.5} stroke="#e4e4e7" />
					<text x="52" y={44 + tick * 52.5} text-anchor="end" font-size="10" fill="#71717a">{formatNumber(tickValue)}</text>
				{/each}

				{#if chartKind === 'line'}
					{#each series as item}
						{#if item.threshold}
							<line x1="58" x2="870" y1={yPosition(item.threshold.minimum)} y2={yPosition(item.threshold.minimum)} stroke="#f59e0b" stroke-dasharray="5 4" opacity="0.75" />
							<line x1="58" x2="870" y1={yPosition(item.threshold.maximum)} y2={yPosition(item.threshold.maximum)} stroke="#f59e0b" stroke-dasharray="5 4" opacity="0.75" />
						{/if}
						<path d={linePath(item)} fill="none" stroke={item.color} stroke-width="2.5" />
						{#each item.points as point}
							<circle cx={xPosition(point.timestamp)} cy={yPosition(point.value)} r={point.anomaly ? 5 : 3} fill={point.anomaly ? '#dc2626' : item.color} stroke="white" stroke-width="1.5">
								<title>{item.label}: {formatNumber(point.value)} {item.unit}{point.anomaly ? ' (anomali)' : ''}</title>
							</circle>
						{/each}
					{/each}
				{:else if chartKind === 'bar'}
					{@const points = series.flatMap((item) => item.points.map((point) => ({ ...point, color: item.color, unit: item.unit })))}
					{@const barWidth = Math.max(3, Math.min(28, 780 / Math.max(points.length, 1)))}
					{#each points as point, index}
						<rect x={68 + index * (790 / Math.max(points.length, 1))} y={yPosition(point.value)} width={barWidth} height={250 - yPosition(point.value)} fill={point.anomaly ? '#dc2626' : point.color} rx="2">
							<title>{formatNumber(point.value)} {point.unit}{point.anomaly ? ' (anomali)' : ''}</title>
						</rect>
					{/each}
				{:else}
					{#each series as item, index}
						{@const values = item.points.map((point) => point.value)}
						{@const q1 = percentile(values, 25)}
						{@const median = percentile(values, 50)}
						{@const q3 = percentile(values, 75)}
						{@const centerX = 180 + index * (620 / Math.max(series.length, 1))}
						<line x1={centerX} x2={centerX} y1={yPosition(Math.min(...values))} y2={yPosition(Math.max(...values))} stroke={item.color} stroke-width="2" />
						<rect x={centerX - 38} y={yPosition(q3)} width="76" height={Math.max(2, yPosition(q1) - yPosition(q3))} fill={item.color} fill-opacity="0.2" stroke={item.color} stroke-width="2" />
						<line x1={centerX - 38} x2={centerX + 38} y1={yPosition(median)} y2={yPosition(median)} stroke={item.color} stroke-width="3" />
						{#each item.points.filter((point) => point.anomaly) as point}
							<circle cx={centerX + 52} cy={yPosition(point.value)} r="5" fill="#dc2626"><title>{formatNumber(point.value)} {item.unit} (anomali)</title></circle>
						{/each}
						<text x={centerX} y="270" text-anchor="middle" font-size="11" fill="#52525b">{item.label}</text>
					{/each}
				{/if}
			</svg>
		</div>
		<p class="mt-2 text-xs text-zinc-500">Garis putus-putus jingga menunjukkan batas klinis. Titik atau batang merah menunjukkan nilai di luar rentang yang dikonfigurasi.</p>
	{/if}
</section>
