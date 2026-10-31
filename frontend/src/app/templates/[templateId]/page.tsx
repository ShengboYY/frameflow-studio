import { TemplateDetail } from "@/features/template/template-detail";

export default async function TemplatePage({ params }: { params: Promise<{ templateId: string }> }) {
  const { templateId } = await params;
  return <TemplateDetail templateId={templateId} />;
}
