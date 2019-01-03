package com.myproj.ftp;

import com.myproj.tools.FtpUtil;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ftp的批量删除，支持不同目录下的文件上传到指定目录
 * 沈燮
 * 2019/1/3
 **/
public class FtpBatchUpload
{
    private FTPClient client = FtpUtil.init();

    //指定上传到服务器的路径
    private Map<String,String> filePaths = new HashMap<String,String>();

    //存放上传失败的文件的路径
    private Map<String,String> fails = new HashMap<String,String>();

    //分隔符‘\’
    private final String SPLIT_BACKSLASH = "\\";

    //分隔符“/”
    private final String SPLIT_FORWARD_SLASH = "/";

    //批量上传的文本路径
    private String batchUploadFilePath;

    /**
     * ftp上传到指定服务器：根据ip和端口号连接到ftp，根据用户名，密码登录ftp，将二进制文件上到ftp（3次机会），关流，注销ftp用户，ftp断开连接
     * @return 判定是否上传完成
     */
    public Boolean batchUpload()
    {
        Boolean flag = false;

        //对txt数据预处理，如本地或者服务器路径缺失，会即时会退出批量上传操作
        if(processData())
        {
            if(FtpUtil.connectToFtp())
            {
                //开始上传操作
                flag = uploadFileToFtp();
            }
        }
        return flag;
    }

    /**
     * 创建服务器文件，将二进制的文件上传到ftp
     * @return 判定是否上传成功
     */
    public boolean uploadFileToFtp()
    {
        int count = 0;
        Boolean flag = false;
        InputStream is = null;
        String localUploadFilePath = null;
        String remoteUploadFilePath =null;
        List<Boolean> result = new ArrayList<Boolean>();
        try
        {
            for(Map.Entry<String,String> entry : filePaths.entrySet())
            {
                localUploadFilePath = entry.getKey();
                remoteUploadFilePath = entry.getValue();
                System.out.println("localUploadFilePath:"+localUploadFilePath+"\n"+"remoteUploadFilePath:"+remoteUploadFilePath);
                //用io去读本地文件
                is = new FileInputStream(localUploadFilePath);

                String fileName = new File(localUploadFilePath).getName();

                //在远程服务器创建文件
                mkDir(remoteUploadFilePath);

                //linux开启本地被动模式，windows会默认自动开启
                client.enterLocalPassiveMode();

                //设置二进制
                client.setFileType(FTP.BINARY_FILE_TYPE);

                //获得服务器的文本编码格式
                String controlEncoding = client.getControlEncoding();

                //将文件名由utf-8转化为ftp的文字编码格式
                fileName = new String(fileName.getBytes("UTF-8"),controlEncoding);

                // ftp路径:路径必须具体到文件，否则上传不成功
                String remote = (!remoteUploadFilePath.endsWith(SPLIT_FORWARD_SLASH) ? remoteUploadFilePath + SPLIT_FORWARD_SLASH : remoteUploadFilePath) + fileName;

                System.out.println("正在用ftp上传指定文件到服务器");

                //用ftpClient上传到服务器
                flag = client.storeFile(remote, is);

                if(!flag)
                {
                    fails.put(localUploadFilePath,remoteUploadFilePath);
                }

                result.add(flag);

                System.out.println("用ftp传输二进制文件" +(flag.toString().equals("true") ?"成功":"失败"));
            }

            for(Map.Entry<String,String> fail:fails.entrySet())
            {
                System.out.println("****************上传失败的文件路径****************");
                System.out.println("localUploadFilePath:"+localUploadFilePath);
                System.out.println("remoteUploadFilePath:"+remoteUploadFilePath);
            }

            if(result.contains(false))
            {
                return false;
            }
        }
        catch (FileNotFoundException e)
        {
            System.out.println("uploadFileToFtp:failed,localUploadFilePath:"+localUploadFilePath+",\nexception:"+e);
        }
        catch (IOException e)
        {
            System.out.println("uploadFileToFtp:failed"+",\nexception:"+e);
        }
        finally
        {
            //关闭资源
            FtpUtil.closeResources(is,null,client);
        }
        return true;
    }

    /**
     * 判定是否在服务器创建文件成功
     */
    public void mkDir(String remoteUploadFilePath)
    {
        Boolean flag = false;

        try
        {
            int index = remoteUploadFilePath.lastIndexOf(SPLIT_FORWARD_SLASH);

            //去除“/”
            String packageName = remoteUploadFilePath.substring(index+1);

            //如果文件不存在，则创建目录
            if (!client.changeWorkingDirectory(remoteUploadFilePath) )
            {
                System.out.println("正在用ftp在服务器上创建目录：");

                //在shell根目录下创建文件夹
                flag = client.makeDirectory(packageName);

                System.out.println("在服务器创建文件夹" +(flag.toString().equals("true") ?"成功":"失败")+",文件夹名称:" + packageName);
            }
            else
            {
                System.out.println("在服务器已存在该文件夹！");
            }
        }
        catch (IOException e)
        {
            System.out.println("mkDir:fail,remoteUploadFilePath:"+remoteUploadFilePath+",\nexception:"+e);
        }
    }

    /**
     * 对txt文件中的数据进行处理
     */
    public boolean processData()
    {
        try
        {
            String line = null;
            BufferedReader br = new BufferedReader(new FileReader("D:\\newWorkSpace\\ftp\\src\\main\\resources\\ftpBatchOperation.txt"));

            while(null != (line = br.readLine()))
            {
                String[] paths = line.split("              ");
                for(String path:paths)
                {
                    if (line.contains(SPLIT_BACKSLASH) && line.contains(SPLIT_FORWARD_SLASH) && null != path.trim())
                    {
                        if (null != paths[0] && null != paths[1])
                        {
                            filePaths.put(paths[0].trim(), paths[1].trim());
                        }
                    }
                    else if(line.contains(SPLIT_BACKSLASH) || line.contains(SPLIT_FORWARD_SLASH))
                    {
                        System.out.println("在ftpBatchOperation.txt文件中的数据有异常，请及时处理！");

                        return false;
                    }
                }
            }
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return true;
    }

    public String getBatchUploadFilePath()
    {
        return batchUploadFilePath;
    }

    public void setBatchUploadFilePath(String batchUploadFilePath)
    {
        this.batchUploadFilePath = batchUploadFilePath;
    }
}
