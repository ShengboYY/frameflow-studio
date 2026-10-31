import { TemplateDetail } from "@/features/template/template-detail";
import { connection } from "next/server";

export default async function TemplatePage({ params }: { params: Promise<{ templateId: string }> }) {
  const { templateId } = await params;
  // Read the demo flag at runtime so the same build can serve normal and demo environments.
  await connection();
  return <TemplateDetail templateId={templateId} simulationEnabled={process.env.MOCK_CONTROLS_ENABLED === "true"} />;
}
