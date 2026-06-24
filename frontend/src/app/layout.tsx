import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'DocRAG 文件問答',
  description: '上傳文件，以自然語言提問',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-TW">
      <body suppressHydrationWarning>{children}</body>
    </html>
  );
}
