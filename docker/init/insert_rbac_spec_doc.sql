USE agent_platform;

DELETE FROM sys_document WHERE id = 15;

INSERT INTO sys_document (id, title, content, dept_id, security_level, create_time) VALUES
(15, 'RBAC & Security Clearance System Design Specification', '# Role-Based Access Control (RBAC) & Security Clearance Design Specification

This document provides a comprehensive overview of the **Role-Based Access Control (RBAC)** and **Security Clearance Level (SCL)** architecture implemented within the BankAgent platform. It serves as a guide for development teams, system auditors, and instructors.

---

## 1. Core Architectural Concepts

To guarantee financial security and data confidentiality, the platform combines **Role-Based Permissions (RBAC)** with **Mandatory Attribute-Based Isolation (Department & Clearance Levels)**. 

Every user session is bounded by:
1. **System Role (Role-based)**: Dictates what functional features the user can invoke (e.g. executing text-to-SQL, auditing schedules, viewing logs).
2. **Department Allocation (Attribute-based)**: Restricts access to department-specific knowledge assets and databases.
3. **Security Clearance Level (Clearance-based)**: Determines the maximum confidentiality classification the user can retrieve.

---

## 2. Role Definitions & Permissions Matrix

The system pre-defines three major system roles:

| Role Code | Role Name | Allowed Functional Operations | Target Users |
| :--- | :--- | :--- | :--- |
| `ROLE_ADMIN` | System Administrator | Full user management, department roster reorganization, system logs auditing, global configuration editing. | System Auditors / IT Admins |
| `ROLE_DEPT_ADMIN` | Department Administrator | Management of department members, approval of RAG access requests, SQL query audit reviews. | Department Heads / Audit Managers |
| `ROLE_USER` | Standard Employee | Running standard business flows, querying local vector knowledge databases, asking AI agents questions. | Loan Officers / Compliance Staff |

---

## 3. Data Isolation & Security Clearance Levels (SCL)

Documents in the vector database and relational tables are cataloged under three clearance levels:

* **Level-1: Public (L1)**
  * **Scope**: General banking policies, standard operating templates, user handbooks.
  * **Access**: Accessible by any authenticated user, regardless of their department allocation.
* **Level-2: Internal (L2)**
  * **Scope**: Active project notes, standard department manuals, compliance auditing guidelines.
  * **Access**: Restricted to employees *within the same department* who possess a clearance level of **Level-2 or above**.
* **Level-3: Confidential (L3)**
  * **Scope**: Proprietary risk models, high-risk audit investigations, private client evaluation data.
  * **Access**: Restricted to senior members *within the same department* with **Clearance Level-3**.

### Multi-Dimensional Access Control Rule (SQL expression):
```sql
-- Accessible if:
-- 1. Document is global (dept_id IS NULL)
-- 2. OR (User is in the same department AND has sufficient clearance level)
-- 3. OR (User has an approved temporary access bypass token/record)
SELECT * FROM sys_document
WHERE (dept_id IS NULL) 
   OR (dept_id = :user_dept_id AND :user_clearance >= security_level)
   OR EXISTS (
       SELECT 1 FROM sys_notification 
       WHERE notify_type = \'RAG_APPLY\' 
         AND sender_id = :user_id 
         AND status = 3 -- Approved
         AND JSON_EXTRACT(payload, \'$.documentId\') = sys_document.id
   );
```

---

## 4. Human-in-the-Loop (HITL) Clearance Escalation Workflow

When an employee attempts to retrieve an internal document but has insufficient clearance, the platform implements a **Human-in-the-Loop (HITL)** approval flow to prevent rigid denial of service while maintaining strict auditing:

1. **Submission**: The user requests access, specifying a business justification (e.g., "required for Q3 loan risk report"). This generates a notification with type `RAG_APPLY` and status `2` (Read Pending Approval) routed to the department manager.
2. **Evaluation**: The department manager reviews the request. They can approve or reject the request, providing an audit comment.
3. **Activation**: Upon approval, the status changes to `3` (Approved). The document retrieval algorithm detects the approved record, and dynamically grants access to that specific document.', NULL, 1, NOW());
