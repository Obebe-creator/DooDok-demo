import type { Metadata } from "next";
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
    <html lang="ko">
      <body className="antialiased">{children}</body>
    </html>
  );
}
