import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from '@/components/ui/pagination';

interface ListPaginationProps {
  /** Page courante, indexée à partir de 0 (comme l'API). */
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}

/** Pages affichées : première, dernière et voisines de la page courante ; null = ellipse. */
function visiblePages(page: number, totalPages: number): (number | null)[] {
  const pages: (number | null)[] = [];
  for (let i = 0; i < totalPages; i++) {
    if (i === 0 || i === totalPages - 1 || Math.abs(page - i) <= 1) {
      pages.push(i);
    } else if (pages[pages.length - 1] !== null) {
      pages.push(null);
    }
  }
  return pages;
}

export function ListPagination({ page, totalPages, totalElements, onPageChange }: ListPaginationProps) {
  if (totalPages <= 1) return null;
  const isFirst = page === 0;
  const isLast = page >= totalPages - 1;

  return (
    <div className="mt-4 flex flex-col gap-3 px-4 pb-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="text-sm text-muted-foreground">
        Page <span className="font-medium text-foreground">{page + 1}</span> sur{' '}
        <span className="font-medium text-foreground">{totalPages}</span> · {totalElements} au total
      </div>
      <Pagination className="mx-0 w-auto">
        <PaginationContent>
          <PaginationItem>
            <PaginationPrevious
              href="#"
              aria-disabled={isFirst}
              className={isFirst ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
              onClick={(e) => { e.preventDefault(); if (!isFirst) onPageChange(page - 1); }}
            />
          </PaginationItem>
          {visiblePages(page, totalPages).map((p, i) => (
            <PaginationItem key={p === null ? `ellipsis-${i}` : p}>
              {p === null ? (
                <span className="flex h-9 w-9 items-center justify-center" aria-hidden>…</span>
              ) : (
                <PaginationLink
                  href="#"
                  isActive={p === page}
                  onClick={(e) => { e.preventDefault(); onPageChange(p); }}
                >
                  {p + 1}
                </PaginationLink>
              )}
            </PaginationItem>
          ))}
          <PaginationItem>
            <PaginationNext
              href="#"
              aria-disabled={isLast}
              className={isLast ? 'pointer-events-none opacity-50' : 'cursor-pointer'}
              onClick={(e) => { e.preventDefault(); if (!isLast) onPageChange(page + 1); }}
            />
          </PaginationItem>
        </PaginationContent>
      </Pagination>
    </div>
  );
}
