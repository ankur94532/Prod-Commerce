import { useEffect, useMemo, useState } from "react";

function initialsFor(name) {
  if (!name) return "PR";
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();
}

function toneFor(value) {
  const tones = [
    "bg-sky-100 text-sky-800",
    "bg-emerald-100 text-emerald-800",
    "bg-amber-100 text-amber-800",
    "bg-rose-100 text-rose-800",
    "bg-indigo-100 text-indigo-800",
    "bg-slate-200 text-slate-700",
  ];
  const text = value || "";
  const hash = Array.from(text).reduce(
    (sum, char) => sum + char.charCodeAt(0),
    0
  );
  return tones[hash % tones.length];
}

export default function ProductImage({
  src,
  alt,
  category,
  className = "",
  placeholderClassName = "",
}) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [src]);

  const tone = useMemo(() => toneFor(category || alt), [category, alt]);
  const showImage = src && !failed;

  if (showImage) {
    return (
      <img
        src={src}
        alt={alt || ""}
        className={className}
        loading="lazy"
        onError={() => setFailed(true)}
      />
    );
  }

  return (
    <div
      className={`w-full h-full flex flex-col items-center justify-center text-center ${tone} ${placeholderClassName}`}
      aria-label={alt || "Product image placeholder"}
      role="img"
    >
      <span className="text-lg font-semibold tracking-normal">
        {initialsFor(alt)}
      </span>
      {category && (
        <span className="mt-1 max-w-full px-2 text-[10px] font-medium uppercase tracking-normal">
          {category.replace(/-/g, " ")}
        </span>
      )}
    </div>
  );
}
