import localFont from "next/font/local";

/**
 * 서비스 전체 글꼴: HS활공굴림 (토끼네활자공장)
 * 원본 OTF를 woff2로 변환해 저장소에 함께 둔다. (fonts/)
 * 한 굵기에 약 140KB라 전부 미리 받지 않고, 페이지에서 쓰는 굵기만 받도록 preload를 끈다.
 */
export const sans = localFont({
  src: [
    { path: "./fonts/HSHwalgongGulim-Light.woff2", weight: "300", style: "normal" },
    { path: "./fonts/HSHwalgongGulim-SemiLight.woff2", weight: "350", style: "normal" },
    { path: "./fonts/HSHwalgongGulim-Regular.woff2", weight: "400", style: "normal" },
    { path: "./fonts/HSHwalgongGulim-Medium.woff2", weight: "500", style: "normal" },
    { path: "./fonts/HSHwalgongGulim-Bold.woff2", weight: "700", style: "normal" },
  ],
  variable: "--font-hwalgong",
  display: "swap",
  preload: false,
});
