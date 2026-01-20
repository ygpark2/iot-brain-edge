package com.walterwalker.brain.edge.spool

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.concurrent.atomic.AtomicLong

/** Extremely simple, robust spool:
  * - Append each event as one line (NDJSON) to spool.ndjson
  * - Maintain cursor as byte offset (cursor.txt)
  * - Sender reads from cursor offset, sends line by line, advances cursor on success
  *
  * Guarantees:
  * - At-least-once delivery (duplicates possible on crash after send-before-cursor-flush)
  */
final class DiskSpool(val dir: Path):
  private val spoolFile: Path = dir.resolve("spool.ndjson")
  private val cursorFile: Path = dir.resolve("cursor.txt")

  // in-memory cursor cache (bytes)
  private val cursor: AtomicLong = AtomicLong(loadCursor())

  Files.createDirectories(dir)
  if !Files.exists(spoolFile) then Files.createFile(spoolFile)

  /** Append one NDJSON line. Returns the starting byte offset where this line begins. */
  def appendLine(line: String): Long =
    val bytes = (line + "\n").getBytes(StandardCharsets.UTF_8)
    // NOTE: we open+close each time for simplicity/robustness; optimize later with buffered channel
    val startOffset = Files.size(spoolFile)
    Files.write(
      spoolFile,
      bytes,
      StandardOpenOption.WRITE,
      StandardOpenOption.APPEND
    )
    startOffset

  /** Read next line starting at current cursor; returns (line, nextCursorOffset). */
  def readNextFromCursor(): Option[(String, Long)] =
    val start = cursor.get()
    val size = Files.size(spoolFile)
    if start >= size then return None

    // naive but reliable: read a chunk and find newline
    val chunkSize = 64 * 1024
    val maxRead = Math.min(chunkSize.toLong, size - start).toInt
    val buf = new Array[Byte](maxRead)

    val ch = java.nio.channels.FileChannel.open(spoolFile, StandardOpenOption.READ)
    try
      ch.position(start)
      val n = ch.read(java.nio.ByteBuffer.wrap(buf))
      if n <= 0 then None
      else
        val slice = java.util.Arrays.copyOf(buf, n)
        val idx = slice.indexOf('\n'.toByte)
        if idx >= 0 then
          val lineBytes = java.util.Arrays.copyOfRange(slice, 0, idx)
          val line = new String(lineBytes, StandardCharsets.UTF_8)
          val next = start + idx + 1
          Some((line, next))
        else
          // Line longer than chunk; fall back to slow path: read until newline
          Some(readLongLine(start, size))
    finally ch.close()

  private def readLongLine(start: Long, size: Long): (String, Long) =
    val ch = java.nio.channels.FileChannel.open(spoolFile, StandardOpenOption.READ)
    val out = new java.io.ByteArrayOutputStream()
    try
      ch.position(start)
      val bb = java.nio.ByteBuffer.allocate(4096)
      var pos = start
      var done = false
      while !done && pos < size do
        bb.clear()
        val n = ch.read(bb)
        if n <= 0 then
          done = true
        else
          bb.flip()
          var i = 0
          while i < n && !done do
            val b = bb.get()
            pos += 1
            if b == '\n'.toByte then done = true
            else out.write(b)
            i += 1
      val line = out.toString(StandardCharsets.UTF_8)
      (line, pos)
    finally ch.close()

  /** Advance cursor to newOffset and persist immediately. */
  def commitCursor(newOffset: Long): Unit =
    cursor.set(newOffset)
    Files.writeString(cursorFile, newOffset.toString, StandardCharsets.UTF_8,
      StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)

  def currentCursorOffset(): Long = cursor.get()

  private def loadCursor(): Long =
    if Files.exists(cursorFile) then
      val s = Files.readString(cursorFile, StandardCharsets.UTF_8).trim
      s.toLongOption.getOrElse(0L)
    else 0L
