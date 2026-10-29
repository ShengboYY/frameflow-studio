import { cpSync } from "node:fs";

// Next.js standalone output omits static/public files; package them for local and Docker startup.
cpSync(".next/static", ".next/standalone/.next/static", { recursive: true });
cpSync("public", ".next/standalone/public", { recursive: true });
