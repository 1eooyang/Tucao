package me.sweetll.tucao.model.xml

import android.os.Parcel
import android.os.Parcelable
import org.simpleframework.xml.Element
import org.simpleframework.xml.Root
import zlc.season.rxdownload2.entity.DownloadFlag
import zlc.season.rxdownload2.entity.DownloadStatus

@Root(name = "durl")
data class Durl(
        @field:Element(name = "order") var order: Int = 0,
        @field:Element(name = "length") var length: Long = 0L,
        @field:Element(name = "url") var url: String = "",
        var downloadPath: String = "",
        var flag: Int = DownloadFlag.NORMAL,
        var status: DownloadStatus = DownloadStatus()
) : Parcelable {
    companion object {
        @JvmField val CREATOR: Parcelable.Creator<Durl> = object : Parcelable.Creator<Durl> {
            override fun createFromParcel(source: Parcel): Durl = Durl(source)
            override fun newArray(size: Int): Array<Durl?> = arrayOfNulls(size)
        }
    }

    constructor(source: Parcel) : this(source.readInt(), source.readLong(), source.readString(), source.readString(), source.readInt(), source.readParcelable<DownloadStatus>(DownloadStatus::class.java.classLoader))

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeInt(order)
        dest?.writeLong(length)
        dest?.writeString(url)
        dest?.writeString(downloadPath)
        dest?.writeInt(flag)
        dest?.writeParcelable(status, 0)
    }
}
