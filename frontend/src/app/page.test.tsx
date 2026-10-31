import { render, screen, within } from "@testing-library/react";
import { afterEach, expect, test, vi } from "vitest";
import Home from "@/app/page";
import { templateApi } from "@/features/template/template-api";
import { portrait } from "@/test/fixtures";

afterEach(() => vi.restoreAllMocks());

test("the home page opens directly into the browsable template catalog", async () => {
  vi.spyOn(templateApi, "categories").mockResolvedValue([]);
  vi.spyOn(templateApi, "list").mockResolvedValue([portrait]);
  render(<Home />);
  const main = within(screen.getByRole("main"));
  expect(main.getByRole("heading", { level: 1, name: "选择你的灵感起点" })).toBeVisible();
  expect(await main.findByRole("link", { name: "查看肖像卡片" })).toHaveAttribute("href", "/templates/portrait-card");
});
