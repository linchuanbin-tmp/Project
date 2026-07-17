-- Demo-grade RAG documents for instructor walkthroughs.
-- Run after patch_rag_tables.sql when an existing MySQL volume needs richer RAG seed content.

INSERT INTO `sys_document`
(`id`, `title`, `content`, `dept_id`, `security_level`, `create_time`, `file_type`, `parse_status`)
VALUES
(7, 'Q1 2025 Credit Assessment Manual',
'Purpose: This manual defines the Q1 2025 workflow for corporate credit assessment.

Scope: Credit department managers and analysts use this manual to review borrower profiles, industry exposure, repayment capacity, collateral quality, and early-warning signals.

Standard process:
1. Collect borrower financial statements, ownership structure, repayment history, and current facility usage.
2. Calculate leverage, interest coverage, cash-flow adequacy, and concentration exposure.
3. Compare the borrower against sector benchmarks and internal risk appetite.
4. Assign a preliminary credit grade and document all exceptions.
5. Escalate cases with weak cash flow, related-party transactions, or material covenant pressure.

Recommended answer focus: credit assessment steps, risk indicators, escalation conditions, and documentation requirements.',
1, 2, NOW(), 'MARKDOWN', 'DONE'),

(8, 'Confidential Credit Risk Evaluation for Corporate Accounts',
'Purpose: This confidential guide describes advanced credit risk evaluation rules for large corporate accounts.

Access warning: This document is Level 3 and should only be retrieved by users with sufficient clearance or temporary approval.

Confidential evaluation rules:
1. Apply stricter debt-service coverage thresholds for borrowers with volatile operating cash flow.
2. Treat undisclosed guarantees, circular financing, and aggressive revenue recognition as high-risk signals.
3. Require senior review before increasing exposure for customers with declining collateral liquidity.
4. Limit approval authority when multiple facilities mature within the same reporting quarter.
5. Use enhanced monitoring for borrowers with rapid changes in ownership, pledge ratios, or related-party receivables.

Recommended answer focus: confidential risk signals, senior review triggers, and credit authorization limits.',
1, 3, NOW(), 'MARKDOWN', 'DONE'),

(9, 'Standard Loan Agreement Template',
'Purpose: This public credit department template lists standard clauses for corporate loan agreements.

Core clauses:
1. Borrower identity, facility amount, loan purpose, interest rate, repayment schedule, and maturity date.
2. Conditions precedent, including valid authorization, corporate resolutions, and required supporting documents.
3. Borrower undertakings, including timely financial reporting, permitted use of funds, and notification of material adverse events.
4. Covenants covering leverage, asset disposal, cross-default, collateral maintenance, and change of control.
5. Default handling, acceleration rights, penalty interest, collateral enforcement, and dispute resolution.

Recommended answer focus: standard agreement clauses and obligations that are safe for Level 1 users.',
1, 1, NOW(), 'MARKDOWN', 'DONE'),

(10, 'AML Compliance Operational Handbook',
'Purpose: This handbook defines internal AML monitoring and suspicious transaction reporting procedures.

Operational procedures:
1. Monitor unusual transaction frequency, abnormal counterparties, rapid fund movement, and inconsistent customer behavior.
2. Compare activity with customer profile, expected business scale, geographic risk, and historical baseline.
3. Record evidence, screenshots, transaction references, investigator notes, and preliminary risk classification.
4. Escalate suspicious activity reports to the compliance manager within the required internal review window.
5. Preserve audit trails and avoid informing the customer when a suspicious activity review is underway.

Recommended answer focus: AML monitoring indicators, reporting steps, evidence retention, and escalation workflow.',
2, 2, NOW(), 'MARKDOWN', 'DONE'),

(11, 'Internal Audit Code of Conduct',
'Purpose: This public compliance document explains internal audit conduct requirements.

Audit principles:
1. Auditors must remain independent, objective, and evidence-driven.
2. Audit plans should define scope, responsible staff, key risk areas, and review timelines.
3. Findings must distinguish confirmed issues, observations, and improvement recommendations.
4. Working papers should preserve source evidence, interview notes, sample selection logic, and approval traces.
5. Audit communication should be factual, respectful, and limited to authorized stakeholders.

Recommended answer focus: audit behavior, evidence standards, reporting discipline, and professional conduct.',
2, 1, NOW(), 'MARKDOWN', 'DONE'),

(12, 'High Risk Client Investigation Guidelines',
'Purpose: This confidential compliance manual describes investigation rules for high-risk clients.

Access warning: This document is Level 3 and should only be retrieved by authorized compliance users or approved temporary access.

Investigation focus:
1. Review shell-company indicators, complex ownership chains, nominee directors, and unusual beneficial ownership changes.
2. Examine high-value cross-border flows, round-number transfers, rapid fund layering, and unexplained third-party payments.
3. Collect enhanced due diligence evidence, including corporate registry records, sanctions screening, adverse media, and source-of-funds explanations.
4. Escalate cases involving politically exposed persons, sanctioned jurisdictions, forged documentation, or repeated suspicious activity alerts.
5. Preserve strict confidentiality and record every access, decision, and approval in the audit trail.

Recommended answer focus: high-risk investigation indicators, enhanced due diligence, and escalation triggers.',
2, 3, NOW(), 'MARKDOWN', 'DONE'),

(13, 'BankAgent Platform User Manual',
'Purpose: This manual introduces the BankAgent enterprise operations platform.

Platform functions:
1. Tool Agent supports meeting room booking, schedule checking, route planning, and operational actions through natural language.
2. Code Agent helps authorized users generate and validate SQL for enterprise databases.
3. RAG Agent retrieves grounded answers from enterprise documents and returns citations for traceability.
4. Task Center tracks agent-generated work items, review steps, and execution status.
5. Messages and notifications support approval workflows, including temporary RAG document access requests.

Recommended answer focus: main platform modules, agent responsibilities, and how RAG supports grounded answers.',
NULL, 1, NOW(), 'MARKDOWN', 'DONE'),

(14, 'Security Operations & Clearance Levels Guide',
'Purpose: This guide explains how BankAgent handles department isolation and clearance levels.

Clearance model:
1. Level 1 Public: available to users whose department and role permit normal document access.
2. Level 2 Internal: available to managers or users with higher clearance inside the relevant department.
3. Level 3 Confidential: restricted to highly trusted users or users with explicit temporary approval.
4. Global documents have no department id and can be used across departments if the clearance requirement is satisfied.
5. Department documents require both department match and sufficient clearance unless a temporary RAG approval exists.
6. RAG answers must be based only on permission-safe retrieved chunks and should return no-context when safe evidence is unavailable.

Recommended answer focus: public/internal/confidential levels, department filtering, temporary access, and no-context behavior.',
NULL, 1, NOW(), 'MARKDOWN', 'DONE')
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `content` = VALUES(`content`),
  `dept_id` = VALUES(`dept_id`),
  `security_level` = VALUES(`security_level`),
  `file_type` = VALUES(`file_type`),
  `parse_status` = VALUES(`parse_status`);
