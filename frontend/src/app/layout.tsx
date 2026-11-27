import type { Metadata } from "next";
import { MobileTabBar } from "@/components/layout/MobileTabBar";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { AuthProvider } from "@/lib/auth/AuthProvider";
import { sans } from "./fonts";
import "./globals.css";

export const metadata: Metadata = {
  title: "두독 DooDok",
  description: "읽은 책은 다음 독자에게, 감상은 책과 함께",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko" className={sans.variable}>
      {/* 모바일에서는 하단 탭바(h-16)만큼 아래 여백을 둔다. */}
      <body className="flex min-h-svh flex-col pb-[calc(4rem+env(safe-area-inset-bottom))] antialiased md:pb-0">
        <AuthProvider>
          <SiteHeader />
          <div className="flex-1">{children}</div>
          <SiteFooter />
          <MobileTabBar />
        </AuthProvider>
      </body>
    </html>
  );
}
