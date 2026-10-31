import { fireEvent, render, screen } from "@testing-library/react";
import { expect, test } from "vitest";
import { TemplateExamples } from "./template-examples";

test("portrait and travel comparisons show their own source photos and results when switching templates", () => {
  const page = render(<TemplateExamples key="portrait-card" templateId="portrait-card" />);
  fireEvent.click(screen.getByText("查看原图与模板效果"));
  expect(screen.getByAltText("示例原图 1")).toHaveAttribute("src", expect.stringContaining("/demo-examples/portrait-source.jpg"));
  expect(screen.queryByAltText("示例原图 2")).not.toBeInTheDocument();
  expect(screen.getByAltText("套用当前模板的效果示例")).toHaveAttribute("src", expect.stringContaining("/demo-examples/portrait-result.jpg"));
  expect(screen.getByRole("link", { name: "下载原图 1" })).toHaveAttribute("download");
  expect(screen.getByText(/不会根据本次上传图片生成/)).toBeVisible();

  page.rerender(<TemplateExamples key="travel-postcard" templateId="travel-postcard" />);
  fireEvent.click(screen.getByText("查看原图与模板效果"));
  expect(screen.getByAltText("示例原图 1")).toHaveAttribute("src", expect.stringContaining("/demo-examples/travel-coast-source.jpg"));
  expect(screen.getByAltText("示例原图 2")).toHaveAttribute("src", expect.stringContaining("/demo-examples/travel-town-source.jpg"));
  expect(screen.getByAltText("套用当前模板的效果示例")).toHaveAttribute("src", expect.stringContaining("/demo-examples/postcard-result.jpg"));
});

test("example image failure is visible without hiding the source download", () => {
  render(<TemplateExamples templateId="portrait-card" />);
  fireEvent.click(screen.getByText("查看原图与模板效果"));
  fireEvent.error(screen.getByAltText("示例原图 1"));
  expect(screen.getByRole("status")).toHaveTextContent("示例原图 1暂不可用");
  expect(screen.getByRole("link", { name: "下载原图 1" })).toBeVisible();
});

test("a template without a curated example never shows another template's photos", () => {
  const page = render(<TemplateExamples templateId="unknown" />);
  expect(page.container).toBeEmptyDOMElement();
});
