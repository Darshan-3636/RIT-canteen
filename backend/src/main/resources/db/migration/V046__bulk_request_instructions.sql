-- Preserve customer pickup instructions on submitted requests without changing historical rows.
ALTER TABLE bulk_requests ADD COLUMN instructions TEXT NOT NULL DEFAULT ''
    CHECK (length(instructions) <= 500);
