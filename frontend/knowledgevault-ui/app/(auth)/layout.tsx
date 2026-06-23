import React from "react";

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-zinc-950 font-sans antialiased">
      {/* Dynamic Background Gradients */}
      <div className="absolute inset-0 overflow-hidden">
        <div className="absolute -top-[40%] -left-[20%] h-[80%] w-[60%] rounded-full bg-indigo-500/10 blur-[120px]" />
        <div className="absolute -bottom-[40%] -right-[20%] h-[80%] w-[60%] rounded-full bg-violet-600/10 blur-[120px]" />
      </div>
      
      {/* Main Container */}
      <div className="relative z-10 w-full px-4 py-8">
        {children}
      </div>
    </div>
  );
}
