import { forwardToJava } from "@/lib/server/java-proxy";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

export const GET = forwardToJava;
export const POST = forwardToJava;
export const PUT = forwardToJava;
export const PATCH = forwardToJava;
export const DELETE = forwardToJava;
export const HEAD = forwardToJava;
export const OPTIONS = forwardToJava;
