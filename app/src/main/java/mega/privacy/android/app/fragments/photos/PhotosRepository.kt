package mega.privacy.android.app.fragments.photos

import android.content.Context
import android.os.Handler
import android.text.TextUtils
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mega.privacy.android.app.DatabaseHandler
import mega.privacy.android.app.R
import mega.privacy.android.app.listeners.BaseListener
import mega.privacy.android.app.utils.ThumbnailUtilsLollipop.getThumbFolder
import mega.privacy.android.app.utils.Util
import nz.mega.sdk.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

class PhotosRepository @Inject constructor(
    private val megaApi: MegaApiAndroid,
    private val dbHandler: DatabaseHandler,
    @ApplicationContext private val context: Context
) {
    private var order = MegaApiJava.ORDER_MODIFICATION_DESC

    // LinkedHashMap guarantees that the index order of elements is consistent with
    // the order of putting. Moreover, it has a quick element search[O(1)] (for
    // the callback of megaApi.getThumbnail())
    private val photoNodesMap: MutableMap<Long, PhotoNode> = LinkedHashMap()
    private val savedPhotoNodesMap: MutableMap<Long, PhotoNode> = LinkedHashMap()

    private var waitingForRefresh = false

    private val _photoNodes = MutableLiveData<List<PhotoNode>>()
    val photoNodes: LiveData<List<PhotoNode>> = _photoNodes

    suspend fun getPhotos(forceUpdate: Boolean) {
        if (forceUpdate) {
            withContext(Dispatchers.IO) {
                saveAndClearData()
                getPhotoNodes()

                // Update LiveData must in main thread
                withContext(Dispatchers.Main) {
                    _photoNodes.value = ArrayList<PhotoNode>(photoNodesMap.values)
                }
            }
        } else {
            _photoNodes.value = ArrayList<PhotoNode>(photoNodesMap.values)
        }
    }

    /**
     * Save some field values (e.g. "selected") which do not exist in the raw MegaNode data.
     * Restore these values in event of querying the raw data again
     */
    private fun saveAndClearData() {
        savedPhotoNodesMap.clear()
        photoNodesMap.toMap(savedPhotoNodesMap)
        photoNodesMap.clear()
    }

    private fun getThumbnail(node: MegaNode): File? {
        val thumbFile = File(
            getThumbFolder(context),
            node.base64Handle.plus(".jpg")
        )

        return if (thumbFile.exists()) {
            thumbFile
        } else {
            megaApi.getThumbnail(node, thumbFile.absolutePath, object : BaseListener(context) {
                override fun onRequestFinish(
                    api: MegaApiJava?,
                    request: MegaRequest?,
                    e: MegaError?
                ) {
                    request?.let {
                        // Must generate a new PhotoNode object, or the oldItem and newItem in
                        // PhotosGridAdapter's areContentsTheSame will be an identical object,
                        // then the item wouldn't be refreshed
                        photoNodesMap[it.nodeHandle]?.apply {
//                            photoNodesMap[it.nodeHandle] = copy(thumbnail = thumbFile.absoluteFile)
                            thumbnail = thumbFile.absoluteFile
                            uiDirty = true
                        }
                    }

                    refreshLiveData()
                }
            })

            null
        }
    }

    /**
     * Throttle for updating the Photos LiveData
     */
    private fun refreshLiveData() {
        if (waitingForRefresh) return
        waitingForRefresh = true

        Handler().postDelayed(
            {
                waitingForRefresh = false
                _photoNodes.value = ArrayList<PhotoNode>(photoNodesMap.values)
            }, UPDATE_DATA_THROTTLE_TIME
        )
    }

    private fun getPhotoNodes() {
        var lastModifyDate: LocalDate? = null
        var mapKeyTitle = Long.MIN_VALUE

        for (node in getMegaNodesOfPhotos()) {
            val thumbnail = getThumbnail(node)
            val modifyDate = Util.fromEpoch(node.modificationTime)
            val dateString = DateTimeFormatter.ofPattern("MMM uuuu").format(modifyDate)

            if (lastModifyDate == null
                || YearMonth.from(lastModifyDate) != YearMonth.from(
                    modifyDate
                )
            ) {
                lastModifyDate = modifyDate
                photoNodesMap[mapKeyTitle++] =
                    PhotoNode(PhotoNode.TYPE_TITLE, -1, null, -1, dateString, null, false)
            }

            val selected = savedPhotoNodesMap[node.handle]?.selected ?: false

            photoNodesMap[node.handle] = PhotoNode(
                PhotoNode.TYPE_PHOTO,
                -1,
                node,
                -1,
                dateString,
                thumbnail,
                selected
            )
        }
    }

    /*
     * TODO: This is a temp mock function for upcoming:
     * MegaNodeList* MegaApi::searchByType(const char *searchString, MegaCancelToken *cancelToken, int order, int type)
     */
    private fun getMegaNodesOfPhotos(): List<MegaNode> {
        var cuHandle: Long = -1
        val pref = dbHandler.preferences

        if (pref != null && pref.camSyncHandle != null) {
            try {
                cuHandle = pref.camSyncHandle.toLong()
            } catch (ignored: java.lang.NumberFormatException) {
            }

            if (megaApi.getNodeByHandle(cuHandle) == null) {
                cuHandle = -1
            }
        }

        if (cuHandle == -1L) {
            for (node in megaApi.getChildren(megaApi.rootNode)) {
                if (node.isFolder && TextUtils.equals(
                        context.getString(R.string.section_photo_sync),
                        node.name
                    )
                ) {
                    cuHandle = node.handle
                    dbHandler.setCamSyncHandle(cuHandle)
                    break
                }
            }
        }

        if (cuHandle == -1L) {
            return Collections.emptyList()
        }

        return megaApi.getChildren(megaApi.getNodeByHandle(cuHandle), order)
    }

    companion object {
        private const val UPDATE_DATA_THROTTLE_TIME =
            500L   // 500ms, user can see the update of photos instantly
    }
}
