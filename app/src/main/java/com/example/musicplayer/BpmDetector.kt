package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * BPM Detection Utility
 * Uses metadata first, then falls back to estimated BPM based on genre/tempo indicators
 * Note: Full TarsosDSP analysis is commented out but can be enabled if dependency is added
 */
object BpmDetector {
    private const val TAG = "BpmDetector"
    private const val DEFAULT_BPM = 120f
    private const val MIN_BPM = 60f
    private const val MAX_BPM = 200f
    private const val PREFS_NAME = "bpm_cache"
    
    /**
     * Detect BPM from audio file
     * First tries to read metadata, then falls back to intelligent estimation
     */
    suspend fun detectBpm(context: Context, uri: Uri): Float = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = getCachedBpm(context, uri.toString())
        if (cached > 0) {
            Log.d(TAG, "Using cached BPM: $cached for $uri")
            return@withContext cached
        }
        
        // Try metadata first (fast)
        val metadataBpm = getMetadataBpm(context, uri)
        if (metadataBpm > 0) {
            Log.d(TAG, "Got BPM from metadata: $metadataBpm")
            cacheBpm(context, uri.toString(), metadataBpm)
            return@withContext metadataBpm.coerceIn(MIN_BPM, MAX_BPM)
        }
        
        // Estimate based on genre and other metadata
        val estimatedBpm = estimateBpmFromMetadata(context, uri)
        Log.d(TAG, "Estimated BPM: $estimatedBpm")
        cacheBpm(context, uri.toString(), estimatedBpm)
        
        estimatedBpm
    }
    
    /**
     * Read BPM from audio file metadata
     */
    private fun getMetadataBpm(context: Context, uri: Uri): Float {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            
            // Try to extract BPM from various possible metadata keys
            // Some files store it in METADATA_KEY_COMPILATION or custom fields
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            
            // Some music apps store BPM in the genre field like "Electronic (128 BPM)"
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val genreBpm = extractBpmFromString(genre)
            if (genreBpm > 0) return genreBpm
            
            // Check title for BPM info
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val titleBpm = extractBpmFromString(title)
            if (titleBpm > 0) return titleBpm
            
            return 0f
        } catch (e: Exception) {
            Log.w(TAG, "Could not read BPM from metadata", e)
            return 0f
        } finally {
            retriever.release()
        }
    }
    
    /**
     * Extract BPM number from a string like "Electronic (128 BPM)" or "Song Title 140bpm"
     */
    private fun extractBpmFromString(text: String?): Float {
        if (text.isNullOrEmpty()) return 0f
        
        // Match patterns like "128 BPM", "128bpm", "(128)"
        val patterns = listOf(
            Regex("""(\d{2,3})\s*bpm""", RegexOption.IGNORE_CASE),
            Regex("""bpm\s*(\d{2,3})""", RegexOption.IGNORE_CASE),
            Regex("""\((\d{2,3})\)"""),
            Regex("""@\s*(\d{2,3})""")
        )
        
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val bpm = match.groupValues[1].toFloatOrNull()
                if (bpm != null && bpm in MIN_BPM..MAX_BPM) {
                    return bpm
                }
            }
        }
        
        return 0f
    }
    
    /**
     * Estimate BPM based on genre and other metadata
     */
    private fun estimateBpmFromMetadata(context: Context, uri: Uri): Float {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.lowercase() ?: ""
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.lowercase() ?: ""
            
            // Genre-based BPM estimation
            return when {
                // Fast genres (140-180 BPM)
                genre.contains("drum") || genre.contains("dnb") || 
                genre.contains("hardstyle") || genre.contains("hardcore") -> 170f
                
                // Medium-fast genres (120-140 BPM)
                genre.contains("house") || genre.contains("techno") || 
                genre.contains("trance") || genre.contains("edm") || 
                genre.contains("dance") -> 128f
                
                // Medium genres (100-120 BPM)
                genre.contains("pop") || genre.contains("rock") || 
                genre.contains("electronic") -> 110f
                
                // Slow genres (80-100 BPM)
                genre.contains("hip") || genre.contains("rap") || 
                genre.contains("r&b") || genre.contains("trap") -> 90f
                
                // Very slow genres (60-80 BPM)
                genre.contains("ambient") || genre.contains("chillout") || 
                genre.contains("downtempo") || genre.contains("ballad") -> 75f
                
                // Check title for tempo indicators
                title.contains("fast") || title.contains("speed") -> 140f
                title.contains("slow") || title.contains("chill") -> 80f
                
                // Default
                else -> DEFAULT_BPM
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not estimate BPM", e)
            return DEFAULT_BPM
        } finally {
            retriever.release()
        }
    }
    
    /**
     * Cache BPM to avoid repeated detection
     */
    private fun cacheBpm(context: Context, uri: String, bpm: Float) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putFloat(uri, bpm).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache BPM", e)
        }
    }
    
    /**
     * Get cached BPM
     */
    private fun getCachedBpm(context: Context, uri: String): Float {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getFloat(uri, 0f)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get cached BPM", e)
            0f
        }
    }
    
    /**
     * Convert BPM to animation duration in milliseconds
     * Returns duration for one beat
     */
    fun bpmToAnimationDuration(bpm: Float): Long {
        return (60000 / bpm).toLong() // 60000ms = 1 minute
    }
    
    /**
     * Clear BPM cache (useful if user wants to force re-detection)
     */
    fun clearCache(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            Log.d(TAG, "BPM cache cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear cache", e)
        }
    }
}
