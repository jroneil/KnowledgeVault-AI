# KnowledgeVault AI

## Product Requirements Document (PRD)

Version: 1.0
Status: Draft
Author: Joe O'Neil
Date: June 2026

---

# Executive Summary

KnowledgeVault AI is a white-label, AI-powered document intelligence platform designed for small and medium-sized organizations that manage large collections of technical documentation.

The platform enables users to upload, organize, search, and interact with thousands of documents using Retrieval Augmented Generation (RAG) and Large Language Models (LLMs).

Users can ask natural language questions and receive answers backed by source citations from indexed documents.

The platform is designed to be self-hosted, brandable, licensable, and easily deployable for customers.

---

# Vision

Transform static document repositories into searchable knowledge systems that preserve organizational expertise and dramatically reduce the time required to locate critical information.

---

# Problem Statement

Organizations often maintain:

* Technical manuals
* Standard operating procedures
* Engineering documentation
* Safety documents
* Quality procedures
* Service bulletins
* Internal knowledge bases

These documents are typically stored in:

* Shared network drives
* SharePoint sites
* File servers
* Local folders

Finding information requires:

* Knowing where documents are located
* Opening multiple files
* Searching manually
* Reading large amounts of content

This results in:

* Lost productivity
* Inconsistent answers
* Knowledge silos
* Risk when experienced employees retire

---

# Target Market

## Primary Market

Small to Medium Businesses

Examples:

* Manufacturing companies
* Engineering firms
* Equipment dealers
* Construction companies
* Utilities
* Service organizations
* Municipal departments

Employee Count:

* 25–500 employees

Document Count:

* 500–50,000 documents

---

# Product Goals

## Business Goals

* Create a reusable white-label platform.
* Enable one-time deployment and licensing.
* Minimize custom development per customer.
* Support self-hosted deployments.
* Generate recurring maintenance revenue.

## User Goals

* Find answers quickly.
* Verify answers with citations.
* Upload and manage documents.
* Preserve organizational knowledge.
* Reduce onboarding time for employees.

---

# Core Value Proposition

Users should be able to ask:

"How do I calibrate the pressure sensor?"

and receive:

* A direct answer
* Supporting citations
* Source document references
* Page numbers
* Relevant sections

within seconds.

---

# Product Architecture

## Frontend

Technology:

* Next.js
* TypeScript
* Tailwind CSS

Responsibilities:

* User interface
* Authentication screens
* Document management
* Chat interface
* Administration
* Branding management

---

## Spring Boot Application

Technology:

* Spring Boot
* Spring Security
* Spring Data JPA

Responsibilities:

* Authentication
* Authorization
* User Management
* Role Management
* Document Metadata
* Collections
* Audit Logging
* Licensing
* System Configuration
* REST API Gateway

Spring Boot acts as the primary business application.

---

## FastAPI AI Service

Technology:

* FastAPI
* Python AI Ecosystem

Responsibilities:

* Document Processing
* OCR
* Chunking
* Embedding Generation
* Semantic Search
* Hybrid Search
* RAG Pipeline
* LLM Integration

FastAPI acts as the AI engine.

---

## Database

Technology:

* PostgreSQL
* pgvector

Stores:

* Users
* Documents
* Metadata
* Roles
* Permissions
* Audit Logs
* Embeddings
* Chunks

---

## AI Models

Default:

* Ollama

Supported Models:

* Qwen
* Llama
* Gemma
* DeepSeek

Embedding Models:

* nomic-embed-text
* bge-m3
* mxbai-embed-large

Future Providers:

* OpenAI
* Azure OpenAI
* Anthropic

---

# Functional Requirements

## Authentication

### Login

Support:

* Username/Password

Future:

* LDAP
* Azure AD
* Okta
* OIDC

### Roles

Administrator

Can:

* Manage users
* Configure system
* Upload documents
* Manage collections

Contributor

Can:

* Upload documents
* Re-index documents

Viewer

Can:

* Search documents
* Use AI chat

---

# White Label Branding

Administrator can configure:

* Company Name
* Logo
* Login Background
* Primary Color
* Secondary Color
* Footer Text

No code changes required.

---

# License Management

System supports:

* License Keys
* Expiration Dates
* Feature Flags

License Types:

Basic

Professional

Enterprise

---

# Document Collections

Collections provide logical separation.

Examples:

Service Manuals

Engineering Specifications

Safety Procedures

HR Policies

Quality Documentation

Users may search:

* Single Collection
* Multiple Collections
* Entire Repository

---

# Document Upload

Supported Formats

* PDF
* DOCX
* TXT
* HTML
* CSV

Future:

* XLSX
* PPTX

Features:

* Drag and Drop
* Bulk Upload
* Folder Upload
* Metadata Entry

---

# Metadata Management

Store:

* Title
* Category
* Product
* Revision
* Department
* Tags
* Upload Date
* Collection

---

# Version Management

Users can:

* Upload revisions
* Archive documents
* Rebuild indexes
* View version history

---

# Document Processing Pipeline

Step 1

Upload Document

Step 2

Extract Text

Step 3

OCR (Optional)

Step 4

Chunk Content

Step 5

Generate Embeddings

Step 6

Store Embeddings

Step 7

Update Search Index

All processing occurs asynchronously.

---

# OCR Support

Support scanned documents.

Potential Engines:

* Tesseract
* EasyOCR

---

# Search

## Semantic Search

Example:

"What causes low hydraulic pressure?"

Returns conceptually relevant results.

---

## Keyword Search

Traditional text search.

---

## Hybrid Search

Combines:

* Keyword Search
* Semantic Search

---

# AI Chat Assistant

Users may ask questions in natural language.

Example:

"How do I replace the fuel filter?"

System performs:

Question

↓

Embedding

↓

Vector Search

↓

Context Assembly

↓

LLM

↓

Answer Generation

---

# Source Citations

Every answer must include:

* Document Name
* Revision
* Page Number
* Section

Example:

Hydraulic Service Manual Rev 7

Page 42

Section 5.3

Source citations are mandatory.

---

# Source Viewer

Users can:

* Open source document
* Navigate directly to page
* Review supporting content

---

# Administration

## User Management

Create

Edit

Disable

Delete

Users

---

## Audit Logging

Track:

* Logins
* Uploads
* Deletes
* Searches
* AI Questions

---

## System Monitoring

Display:

* Document Count
* Collection Count
* Indexed Chunks
* Storage Usage
* AI Usage Metrics

---

# Deployment

## Self Hosted

Primary deployment model.

Deployment method:

Docker Compose

Example:

docker compose up -d

---

## Managed Hosting

Future offering.

Hosted by vendor.

---

# Non-Functional Requirements

## Performance

Search Results:

< 2 seconds

AI Responses:

< 10 seconds

Upload Acknowledgement:

< 5 seconds

---

## Scalability

Support:

* 50,000 Documents
* 5 Million Chunks
* 100 Concurrent Users

---

## Security

HTTPS

Role Based Access Control

Password Encryption

Audit Logging

Secure API Communication

---

## Backup & Recovery

Support:

* PostgreSQL Backup
* Vector Data Backup
* Configuration Backup
* Restore Procedures

---

# MVP Scope

Version 1.0

Included:

✓ Authentication

✓ User Roles

✓ White Label Branding

✓ PDF Upload

✓ Collections

✓ Metadata Management

✓ Document Versioning

✓ Text Extraction

✓ Chunking

✓ Embeddings

✓ PostgreSQL

✓ pgvector

✓ Ollama Integration

✓ Semantic Search

✓ AI Chat

✓ Source Citations

✓ Audit Logging

✓ Docker Deployment

✓ License Management

---

# Future Roadmap

Version 2.0

* OCR
* SharePoint Connector
* Network Drive Connector
* Saved Searches
* Email Notifications
* Advanced Analytics

Version 3.0

* Agent Workflows
* Knowledge Graphs
* Workflow Automation
* Custom AI Agents
* Multi-Tenant SaaS Platform

---

# Success Metrics

* Search Response < 2 Seconds
* Citation Coverage 100%
* Indexing Success Rate > 99%
* User Satisfaction > 4.5/5
* Customer Renewal Rate > 90%

---

# Product Positioning

KnowledgeVault AI is a white-label document intelligence platform that transforms thousands of static documents into a searchable, AI-powered knowledge base with fully traceable answers and source citations.

Primary differentiator:

"Every answer is backed by the original document and can be verified in seconds."
