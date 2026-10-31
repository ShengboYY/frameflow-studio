import { afterEach, expect, test, vi } from "vitest";
import { connection } from "next/server";
import TemplatePage from "./page";

vi.mock("next/server", () => ({ connection: vi.fn().mockResolvedValue(undefined) }));
afterEach(() => { vi.unstubAllEnvs(); vi.clearAllMocks(); });

test("demo visibility follows the request-time flag without rebuilding or reimporting the page", async () => {
  const page = () => TemplatePage({ params: Promise.resolve({ templateId: "portrait-card" }) });
  vi.stubEnv("MOCK_CONTROLS_ENABLED", undefined);
  expect((await page()).props).toMatchObject({ templateId: "portrait-card", simulationEnabled: false });
  vi.stubEnv("MOCK_CONTROLS_ENABLED", "true");
  expect((await page()).props.simulationEnabled).toBe(true);
  vi.stubEnv("MOCK_CONTROLS_ENABLED", "false");
  expect((await page()).props.simulationEnabled).toBe(false);
  expect(connection).toHaveBeenCalledTimes(3);
});
