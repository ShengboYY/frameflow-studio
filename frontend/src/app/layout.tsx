import type { Metadata } from "next";
import type { ReactNode } from "react";
import { AuthProvider } from "@/features/identity/auth-provider";
import { AuthNavigation } from "@/features/identity/auth-navigation";
import "./globals.css";

export const metadata: Metadata = {
  title: "FrameFlow Studio",
  description: "FrameFlow Studio 素材制作",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN">
      <body><AuthProvider><AuthNavigation />{children}</AuthProvider></body>
    </html>
  );
}
