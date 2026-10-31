import { render, screen, within } from "@testing-library/react";
import { expect, test } from "vitest";
import Home from "@/app/page";

test("renders the home page with a login entry", () => {
  render(<Home />);

  const main = screen.getByRole("main");
  expect(
    within(main).getByRole("heading", { level: 1, name: "FrameFlow Studio" }),
  ).toBeVisible();
  expect(
    within(main).getByRole("link", { name: "进入账号" }),
  ).toBeVisible();
});
