import { render, screen, within } from "@testing-library/react";
import { expect, test } from "vitest";
import Home from "@/app/page";

test("renders the scaffolding home page", () => {
  render(<Home />);

  const main = screen.getByRole("main");
  expect(
    within(main).getByRole("heading", { level: 1, name: "FrameFlow Studio" }),
  ).toBeVisible();
  expect(
    within(main).getByText("项目骨架已初始化，业务功能待后续实现。"),
  ).toBeVisible();
});
