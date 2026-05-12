/**
 * Terms and Conditions content for the DecMed patient client.
 *
 * Structured as an array of sections, each containing typed clauses.
 * Separated from the presentation layer so that:
 *   1. Legal/compliance teams can review content independently.
 *   2. Content can be swapped per locale without modifying component logic.
 *   3. Each section maps 1:1 to a required agreement checkbox in the UI.
 *
 * Content policy:
 *   - No jurisdiction-specific statutory references. All obligations are
 *     expressed as platform governance rules, not citations to national law.
 *   - PGHD consent is explicitly irrevocable after acceptance throughout,
 *     consistent with the platform's immutable audit-trail design.
 */

export type TermsClause = {
	heading: string;
	body: string;
};

export type TermsSection = {
	/** Unique identifier used for DOM ids and state tracking. */
	id: string;
	/** Section title displayed in the header. */
	title: string;
	/** Lucide icon name rendered beside the title. */
	icon: string;
	/** Short summary shown below the title before the full clauses. */
	summary: string;
	/** The individual clauses within this section. */
	clauses: TermsClause[];
	/** Label text for the per-section agreement checkbox. */
	checkboxLabel: string;
};

export const TERMS_SECTIONS: TermsSection[] = [
	{
		id: 'general',
		title: 'General Terms & PGHD Consent Framework',
		icon: 'scroll-text',
		summary:
			'These terms govern your use of the DecMed Patient-Generated Health Data (PGHD) experience. By proceeding, you confirm that you have read each section in full and explicitly agree to all terms below.',
		clauses: [
			{
				heading: '1.1 Acceptance of Terms',
				body: 'By accessing or using the DecMed application, you confirm that you are at least 18 years old (or have verifiable parental/guardian consent) and agree to be bound by these terms. If you do not agree, you must not use this application.'
			},
			{
				heading: '1.2 PGHD Collection Scope',
				body: 'DecMed collects patient-generated health data (PGHD) from your approved device sensors and from your in-app health interactions. This PGHD is used to support your longitudinal health record and authorized care workflows.'
			},
			{
				heading: '1.3 Account Responsibilities',
				body: 'You are responsible for maintaining the confidentiality of your PIN, seed words, and any authentication credentials. Activity performed under your account is your responsibility. You must notify DecMed immediately if you suspect unauthorized access.'
			},
			{
				heading: '1.4 Accuracy of Information',
				body: 'You agree to provide accurate, current, and complete information during registration and while using the platform. False or misleading information may result in suspension or termination of access.'
			},
			{
				heading: '1.5 Irreversible Decision After Acceptance',
				body: 'Once you accept these Terms and PGHD consent, that acceptance decision is final for your account context and cannot be revoked, withdrawn, or changed through in-app controls. This irrevocability is a deliberate design choice that ensures the integrity of your longitudinal PGHD record. If you do not agree with this condition, you must decline before continuing.'
			},
			{
				heading: '1.6 Limitation of Liability',
				body: 'DecMed is provided on an "as-is" basis. To the maximum extent permitted by applicable platform governance agreements, DecMed developers and operators are not liable for indirect, incidental, special, consequential, or punitive damages resulting from use of the platform.'
			},
			{
				heading: '1.7 Modifications to Terms',
				body: 'DecMed may update these terms over time. Material changes will be communicated in-app. Continued use after such updates indicates acceptance of the revised terms.'
			}
		],
		checkboxLabel: 'I have read and agree to the General Terms & PGHD Consent Framework.'
	},
	{
		id: 'data-access',
		title: 'PGHD Access by Authorized Care Parties',
		icon: 'hospital',
		summary:
			'This section describes how authorized healthcare organizations and approved personnel may access and act on your PGHD-linked records within DecMed.',
		clauses: [
			{
				heading: '2.1 Authorized Access',
				body: 'By agreeing to this section, you consent to authorized healthcare organizations and credentialed personnel accessing your records where PGHD is included. Access is session-bound through cryptographic authorization and all access events are auditable.'
			},
			{
				heading: '2.2 Scope of Access',
				body: 'Authorized personnel may access relevant administrative context, clinical records, and PGHD-derived summaries needed for care delivery and continuity.'
			},
			{
				heading: '2.3 Data Modification Rights',
				body: 'Authorized personnel may create and update clinical records related to your care. All changes are versioned and timestamped to preserve traceability.'
			},
			{
				heading: '2.4 Institutional Accountability',
				body: 'Each organization and personnel account interacting with your records is identifiable and auditable. Access and modification events are retained for oversight and verification.'
			},
			{
				heading: '2.5 Emergency Access Context',
				body: 'In emergency contexts, designated providers may receive temporary access to critical records, including relevant PGHD context, with all events recorded for later review.'
			}
		],
		checkboxLabel:
			'I consent to authorized care parties accessing and updating my records with PGHD context as described above.'
	},
	{
		id: 'data-collection',
		title: 'PGHD Collection, Processing, and Retention',
		icon: 'database',
		summary:
			'This section covers data gathered from device sensors and patient inputs, how it is processed, and how it is retained in DecMed.',
		clauses: [
			{
				heading: '3.1 Sensor Data Collection',
				body: 'DecMed may collect data from approved device sensors, including motion, environmental, and health-related streams supported by your hardware. Collection begins only after your explicit acceptance and sensor configuration.'
			},
			{
				heading: '3.2 PGHD Classification',
				body: 'PGHD includes self-reported inputs and device-generated measurements. DecMed stores PGHD with dedicated classification metadata so it can be distinguished from clinician-verified records.'
			},
			{
				heading: '3.3 Processing and Storage',
				body: 'Collected data is processed and stored using protected data pipelines and encrypted storage layers. Access is restricted to authorized workflows and auditable operations.'
			},
			{
				heading: '3.4 Irrevocable Consent Acknowledgement',
				body: 'By accepting this section, you explicitly acknowledge and agree that PGHD collection consent granted through this acceptance flow is irrevocable. This means it cannot be revoked, altered, or deleted through in-app controls after acceptance. This is a binding, permanent commitment consistent with the platform\'s immutable PGHD record architecture.'
			},
			{
				heading: '3.5 Retention and Integrity',
				body: 'PGHD and related record artifacts are retained according to platform retention policy to preserve continuity, reproducibility, and audit integrity.'
			},
			{
				heading: '3.6 Collection Control Before Start',
				body: 'You choose sensors and collection intervals before collection starts. The manual start action indicates your instruction to begin data capture under the accepted terms.'
			}
		],
		checkboxLabel:
			'I consent to PGHD collection, processing, retention, and the irrevocable-consent acknowledgement described above.'
	},
	{
		id: 'notifications',
		title: 'PGHD Access and Change Notifications',
		icon: 'bell-ring',
		summary:
			'This section describes how DecMed notifies you when records that include PGHD are accessed or modified.',
		clauses: [
			{
				heading: '4.1 Access Notifications',
				body: 'You receive notifications when authorized parties access your records, including actor identity, access type, data scope, and timestamp.'
			},
			{
				heading: '4.2 Modification Notifications',
				body: 'You receive notifications when records are created or updated, including what changed, who initiated the action, and when it occurred.'
			},
			{
				heading: '4.3 System and Oversight Access',
				body: 'Where operational oversight or mandated platform reviews require controlled access, such access remains auditable and is surfaced to you through the notification and access-log experience when available.'
			},
			{
				heading: '4.4 Third-Party Audit Access',
				body: 'If independent auditors are engaged to verify system integrity, their access is controlled, auditable, and limited to agreed review scope.'
			},
			{
				heading: '4.5 Notification Delivery',
				body: 'Notifications are provided via in-app channels and access logs. You are responsible for reviewing these notices regularly.'
			},
			{
				heading: '4.6 Contesting Access',
				body: 'If you believe access was unauthorized or improper, you may submit a dispute through official support channels for investigation and follow-up.'
			}
		],
		checkboxLabel:
			'I acknowledge and agree to receive notifications when PGHD-related records are accessed or modified as described above.'
	}
];

/** localStorage key for persisting T&C acceptance. */
export const TERMS_ACCEPTED_KEY = 'decmed_terms_accepted';

/** localStorage key for persisting language preference. */
export const TERMS_LOCALE_KEY = 'decmed_terms_locale';

// ── Locale infrastructure ──────────────────────────────────────────────

export type Locale = 'en' | 'id';

/**
 * Indonesian locale re-uses the canonical PGHD terms content.
 * Section IDs and legal meaning are identical across locales;
 * only UI chrome strings are translated.
 */
export const TERMS_SECTIONS_ID: TermsSection[] = TERMS_SECTIONS;

/**
 * All hardcoded UI strings used in the Terms page, keyed by locale.
 * This keeps the component template free of inline conditionals for
 * every single string, and makes adding a third language trivial.
 */
export const UI_STRINGS: Record<Locale, {
	pageTitle: string;
	pageSubtitle: string;
	introText: string;
	sectionPrefix: string;
	badgeAgreed: string;
	badgeRead: string;
	badgeUnread: string;
	scrollHint: string;
	masterCheckboxLabel: string;
	masterCheckboxHint: string;
	btnDecline: string;
	btnAccept: string;
	footer: string;
	dialogTitle: string;
	dialogBody1: string;
	dialogBody2: string;
	dialogReturn: string;
}> = {
	en: {
		pageTitle: 'Terms & Conditions',
		pageSubtitle: 'DecMed — Decentralized EHR Management',
		introText:
			'Please carefully read each section below. You must scroll to the bottom of every section and check the agreement box before you can proceed. All sections require your explicit consent.',
		sectionPrefix: 'Section',
		badgeAgreed: 'Agreed',
		badgeRead: 'Read',
		badgeUnread: 'Unread',
		scrollHint: '(scroll to bottom to enable)',
		masterCheckboxLabel:
			'I have read, understood, and agree to all of the above Terms and Conditions, including the irrevocable PGHD consent effect after acceptance.',
		masterCheckboxHint: '(agree to all sections above to enable)',
		btnDecline: 'Decline',
		btnAccept: 'Accept & Continue',
		footer: 'DecMed — Decentralized Electronic Health Record System',
		dialogTitle: 'Unable to Continue',
		dialogBody1:
			'You must accept all Terms and Conditions to use the DecMed application. PGHD consent in this flow is final and irrevocable after acceptance.',
		dialogBody2:
			'If you have concerns about any specific terms, please contact our support team for clarification before making your decision.',
		dialogReturn: 'Return to Terms'
	},
	id: {
		pageTitle: 'Syarat & Ketentuan',
		pageSubtitle: 'DecMed — Manajemen RME Terdesentralisasi',
		introText:
			'Harap baca setiap bagian di bawah ini dengan seksama. Anda harus menggulir ke bagian bawah setiap bagian dan mencentang kotak persetujuan sebelum dapat melanjutkan. Semua bagian memerlukan persetujuan eksplisit Anda.',
		sectionPrefix: 'Bagian',
		badgeAgreed: 'Disetujui',
		badgeRead: 'Dibaca',
		badgeUnread: 'Belum Dibaca',
		scrollHint: '(gulir ke bawah untuk mengaktifkan)',
		masterCheckboxLabel:
			'Saya telah membaca, memahami, dan menyetujui semua Syarat dan Ketentuan di atas, termasuk efek persetujuan PGHD yang tidak dapat dibatalkan setelah diterima.',
		masterCheckboxHint: '(setujui semua bagian di atas untuk mengaktifkan)',
		btnDecline: 'Tolak',
		btnAccept: 'Setuju & Lanjutkan',
		footer: 'DecMed — Sistem Rekam Medis Elektronik Terdesentralisasi',
		dialogTitle: 'Tidak Dapat Melanjutkan',
		dialogBody1:
			'Anda harus menerima semua Syarat dan Ketentuan untuk menggunakan aplikasi DecMed. Persetujuan PGHD pada alur ini bersifat final dan tidak dapat dibatalkan setelah diterima.',
		dialogBody2:
			'Jika Anda memiliki kekhawatiran tentang syarat tertentu, silakan hubungi tim dukungan kami untuk klarifikasi sebelum mengambil keputusan.',
		dialogReturn: 'Kembali ke Syarat'
	}
};