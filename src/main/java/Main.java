import com.google.gson.Gson;
import jdk.nashorn.internal.scripts.JD;
import org.apache.regexp.RE;
import spark.Filter;
import spark.Request;
import spark.Response;
import sun.security.jgss.GSSCaller;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static spark.Spark.*;

public class Main {
    public static void main(String[] args) {
        updateNews();
        enableCORS("*", "*", "*");

        get("/hello", (request, response) -> "Hello World");

        get("/login", (request, response) -> {
            return "login page";
        });

        post("/login", ((request, response) -> {
            String user_id = request.queryParams("user_id");
            String password = request.queryParams("password");
            log(user_id + " " + password);
            if(password == null || user_id == null)
                return "empty value";
            User user = new User();
            user.setPassword(password);
            user.setUser_id(user_id);
            if(user.isExists()) {
                user = user.getUser();
                if (password.equals(user.getPassword())){
                    response.cookie("user_id", user.getUser_id());
                    response.cookie("password", user.getPassword());
                    Gson gson = new Gson();
                    return String.format("{\"isOk\":true, \"msg\":\"登录成功\",\"user\":%s}", gson.toJson(user)).toString();
                }
                else return "{\"isOk\":false, \"msg\":\"密码错误\"}";
            }
            else
                return "{\"isOk\":false, \"msg\":\"用户不存在\"}";
        }));

        /**
         * 注册1：发送验证码
         */
        post("/register-1", (((request, response) -> {
            String user_id = request.queryParams("user_id");
            String password = request.queryParams("password");
            String user_name = request.queryParams("user_name");
            Functions.log(user_id+ "\t"+user_name + "\t"+password);
            if (user_id==null||user_name==null||password==null)
                return "empty value";
            User user = new User();
            user.setUser_id(user_id);
            user.setPassword(password);
            String code = null;
            if (user.isExists()){
                user = user.getUser();
                return "{\"isOk\":false, \"msg\":\"用户已存在\""+"}";
            }
            else {
                user.setToken(Functions.getRandomString(30));
                code = Functions.getRandomString(6);
                Functions.sendEmail("验证码", code, user_id);
            }
            return "{\"isOk\":true, \"msg\":\""+code+"\"}";
        })));

        /**
         * 登录2：插入数据库
         */
        post("/register-2", (((request, response) -> {
            String user_id = request.queryParams("user_id");
            String password = request.queryParams("password");
            String user_name = request.queryParams("user_name");
            Functions.log(user_id+ "\t"+user_name + "\t"+password);
            if (user_id==null||user_name==null||password==null)
                return "empty value";
            User user = new User();
            user.setPassword(password);
            user.setUser_id(user_id);
            user.setUser_name(user_name);
            Jdbc jdbc = new Jdbc();
            while (true) {
                String token = Functions.getRandomString(30);
                String sql = "select * from public.user where token='" + token + "'";
                Functions.log(sql);
                ResultSet rs = jdbc.querydata(sql);
                List<User> users = Functions.getUserList(rs);
                if (users.size() == 0) {
                    user.setToken(token);
                    break;
                }
            }
            String sql = String.format("insert into public.user(user_id, user_name, password, token) values('%s', '%s', '%s', '%s')"
                    , user.getUser_id(), user.getUser_name(), user.getPassword(), user.getToken());
            Functions.log(sql);
            jdbc.save(sql);
            return String.format("{\"isOk\":true, \"msg\":\"%s\",\"user\":%s}", "注册成功", user.toString());
        })));

        /**
         * 获得帖子的列表
         */
        post("/post", ((request, response) -> {
            String user_id = request.queryParams("user_id");
            String category = request.queryParams("category");
            String sql = String.format("SELECT user_id, user_name, pic, post_id, title, time, content, liked, source, category FROM public.user NATURAL JOIN public.post WHERE category='%s' ORDER BY time DESC", category).toString();
            int num = 3;
            Jdbc jdbc = new Jdbc();
            ResultSet rs = jdbc.querydata(sql);
            List<Post> posts = new ArrayList<>();
            int i = 0;
            Gson gson = new Gson();
            while (rs.next() && i<num){
                Post post = new Post();
                post.setContent(rs.getString("content"));
                post.setPost_id(rs.getString("post_id"));
                post.setTime(rs.getDate("time"));
                post.setTitle(rs.getString("title"));
                post.setAuthor_name(rs.getString("user_name"));
                post.setAuthor_id(rs.getString("user_id"));
                post.setAuthor_pic(rs.getString("pic"));
                post.setLiked(rs.getInt("liked"));
                post.setLiked(post.isLiked(user_id));
                post.setCategory(rs.getString("category"));
                post.setSource(rs.getString("source"));
                posts.add(post);
                i++;
            }
            return String.format("{"+
                    "\"isOk\": true,"+
            "\"msg\": \"获取成功\","+
                    "\"posts\": %s}", gson.toJson(posts)).toString();
        }));

        post("/get-new-post", (request, response) -> {
            String post_id = request.queryParams("post_id");
            String user_id = request.queryParams("user_id");
            String category = request.queryParams("category");
            int num = 3;
            Jdbc jdbc = new Jdbc();
            String sql = String.format("SELECT user_id, user_name, pic, post_id, title, time, content, liked, source, category FROM public.user NATURAL JOIN public.post WHERE category='%s' ORDER BY time DESC", category).toString();
            System.out.println(sql);
            ResultSet rs = jdbc.querydata(sql);
            List<Post> posts = new ArrayList<>();
            int i = 0;
            Gson gson = new Gson();
            while (rs.next()){
                String cur_post_id = rs.getString("post_id");
                if (cur_post_id.equals(post_id))
                    break;
            }
//            if (!rs.next())
//                return "{\"isOk\":false, \"msg\":\"已经没有更多了\"}";
            while (rs.next()&&i<num) {
                Post post = new Post();
                post.setContent(rs.getString("content"));
                post.setPost_id(rs.getString("post_id"));
                post.setTime(rs.getDate("time"));
                post.setTitle(rs.getString("title"));
                post.setAuthor_name(rs.getString("user_name"));
                post.setAuthor_id(rs.getString("user_id"));
                post.setAuthor_pic(rs.getString("pic"));
                post.setLiked(rs.getInt("liked"));
                post.setLiked(post.isLiked(user_id));
                post.setCategory(rs.getString("category"));
                post.setSource(rs.getString("source"));
                posts.add(post);
                i++;
            }
                return String.format("{"+
                    "\"isOk\": true,"+
                    "\"msg\": \"获取成功\","+
                    "\"posts\": %s}", gson.toJson(posts)).toString();
        });

        /**
         * 发表帖子
         */
        post("/make-new-post", ((((request, response) -> {
            String title = request.queryParams("title");
            String content = request.queryParams("content");
            String user_id = request.queryParams("user_id");
            if (title==null||content==null||user_id==null)
                return "empty value";
            Date date = new Date();
            Post post = new Post();
            post.setContent(content);
            post.setTitle(title);
            post.setTime(date);
            Jdbc jdbc = new Jdbc();
            while (true) {
                String post_id = Functions.getRandomString(30);
                String sql = "select * from public.post where post_id='" + post_id + "'";
                Functions.log(sql);
                ResultSet rs = jdbc.querydata(sql);
                List<String> posts = new ArrayList<>();
                try{
                    while (rs.next())
                        posts.add(rs.getString(1));
                } catch (SQLException e){
                    e.printStackTrace();
                }
                if (posts.size() == 0) {
                    post.setPost_id(post_id);
                    break;
                }
            }
            Gson gson = new Gson();
            String sql = String.format("insert into public.post values('%s', '%s', '%s', '%s', '%s')", post.getAuthor_id(), post.getPost_id(), post.getTitle(), post.getTime(), post.getContent());
            Functions.log(sql);
            jdbc.save(sql);
            return "{\"isOk\":true, \"msg\":\"发表成功\", \"post\":"+gson.toJson(post)+"}";
        }))));

        /**
         *获取帖子的详细内容
         */
        post("/post-details", (request, response) -> {
            String post_id = request.queryParams("post_id");
            String user_id = request.queryParams("user_id");
            String sql = String.format("SELECT * FROM public.comment AS c WHERE c.post_id='%s'", post_id).toString();
            Jdbc jdbc = new Jdbc();
            ResultSet rs = jdbc.querydata(sql);
            List<Comment> comments = new ArrayList<>();
            Gson gson = new Gson();
            while (rs.next()){
                Comment comment = new Comment();
                comment.setPost_id(post_id);
                comment.setContent(rs.getString("content"));
                comment.setDate(rs.getDate("time"));
                String from_id = rs.getString("from_id");
                comment.setFrom_id(from_id);
                comment.setTo_comment_id(rs.getString("to_comment_id"));
                comment.setComment_id(rs.getString("comment_id"));
                String to_id = rs.getString("to_id");
                if (to_id!=null) {
                    comment.setTo_id(to_id);
                    sql = String.format("select * from public.user where user_id='%s'", to_id).toString();
                    Jdbc jdbc1 = new Jdbc();
                    ResultSet rs1 = jdbc1.querydata(sql);
                    while (rs1.next()) {
                        comment.setTo_name(rs1.getString("user_name"));
                        comment.setTo_pic(rs1.getString("pic"));
                    }
                }
                sql = String.format("select * from public.user where user_id='%s'", from_id).toString();
                Jdbc jdbc2 = new Jdbc();
                ResultSet rs2 = jdbc2.querydata(sql);
                while (rs2.next()) {
                    comment.setFrom_name(rs2.getString("user_name"));
                    comment.setFrom_pic(rs2.getString("pic"));
                }
//                while (true){
//                    String comment_id = Functions.getRandomString(30);
//                    String temp = String.format("SELECT * FROM public.comment WHERE comment_id='%s'", comment_id);
//                    Jdbc jdbc8 = new Jdbc();
//                    ResultSet t = jdbc8.querydata(temp);
//                    int i = 0;
//                    while (t.next())
//                        i++;
//                    if (i==0) {
//                        comment.setComment_id(comment_id);
//                        break;
//                    }
//                }
                comments.add(comment);
            }
            Post post = new Post();
            post.setComments(comments);
            sql = String.format("SELECT user_id, user_name, pic, post_id, title, time, content, liked FROM public.user NATURAL JOIN public.post WHERE post_id='%s' ORDER BY time DESC", post_id).toString();
            Jdbc jdbc3 = new Jdbc();
            ResultSet rs3 = jdbc3.querydata(sql);
            while (rs3.next()){
                post.setContent(rs3.getString("content"));
                post.setPost_id(rs3.getString("post_id"));
                post.setTime(rs3.getDate("time"));
                post.setTitle(rs3.getString("title"));
                post.setAuthor_id(rs3.getString("user_id"));
                post.setLiked(rs3.getInt("liked"));
                post.setAuthor_name(rs3.getString("user_name"));
                post.setAuthor_pic(rs3.getString("pic"));
                post.setAuthor_id(rs3.getString("user_id"));
            }
            sql = String.format("SELECT * FROM public.liked WHERE post_id='%s' AND user_id='%s'", post_id, user_id).toString();
            Jdbc jdbc4 = new Jdbc();
            ResultSet rs4 = jdbc4.querydata(sql);
            int i = 0;
            while (rs4.next())
                i++;
            if (i>0)
                post.setLiked(Boolean.TRUE);
            return String.format("{\"isOk\": true, \"msg\": \"返回成功\", \"PostAndComments\": %s}", gson.toJson(post)).toString();
        });

        /**
         * 喜欢按钮
         */
        post("/like", (request, response) -> {
            String post_id = request.queryParams("post_id");
            int liked = Integer.parseInt(request.queryParams("liked"));
            String user_id = request.queryParams("user_id");
            Jdbc jdbc1 = new Jdbc();
            Jdbc jdbc2 = new Jdbc();
            String sql = String.format("UPDATE public.post SET liked = liked+%d WHERE post_id='%s'", liked, post_id).toString();
            User user = new User();
            user.setUser_id(user_id);
            if (user.isExists()){
                jdbc1.edit(sql);
                /**
                 * insert into liked table
                 */
                if (liked==-1){
                    sql = String.format("DELETE FROM public.liked WHERE user_id='%s' AND post_id='%s'", user_id, post_id).toString();
                    jdbc2.delete(sql);
                    return "{\"isOk\":true, \"msg\":\"取消点赞\"}";
                } else if (liked==1){
                    sql = String.format("INSERT INTO public.liked(user_id, post_id) VALUES('%s', '%s')", user_id, post_id).toString();
                    jdbc2.save(sql);
                    return "{\"isOk\":true, \"msg\":\"点赞\"}";
                } else {
                    return "invalid value";
                }

            }
            return "user not exists";
        });

        /**
         * 删除帖子
         */
        post("/delete", (request, response) -> {
            String post_id = request.queryParams("post_id");
            String user_id = request.queryParams("user_id");
            String password = request.queryParams("password");
            System.out.println(password);
            String sql = String.format("SELECT * FROM public.post WHERE post_id='%s' AND user_id='%s'", post_id, user_id).toString();
            Jdbc jdbc4 = new Jdbc();
            User user = new User();
            user.setUser_id(user_id);
            user = user.getUser();
            ResultSet rs4 = jdbc4.querydata(sql);
            Gson gson = new Gson();
            System.out.println(gson.toJson(user));
            int i = 0;
            while (rs4.next())
                i++;
            System.out.println(i);
            if (i>0&&password.equals(user.getPassword())&&user.isExists()){
                Jdbc jdbc1 = new Jdbc();
                Jdbc jdbc2 = new Jdbc();
                Jdbc jdbc3 = new Jdbc();
                sql = String.format("DELETE FROM public.liked WHERE post_id='%s'", post_id).toString();
                jdbc1.delete(sql);
                sql = String.format("DELETE FROM public.comment WHERE post_id='%s'", post_id).toString();
                jdbc2.delete(sql);
                sql = String.format("DELETE FROM public.post WHERE post_id='%s'", post_id).toString();
                jdbc3.delete(sql);
                return "{\"isOk\": true, \"msg\": \"删除成功\"}";
            }
            return "{\"isOk\": false, \"msg\": \"密码错误或帖子不存在\"}";
        });

        /**
         * 评论
         */
        post("/comment", (request, response) -> {
            String post_id = request.queryParams("post_id");
            String from_id = request.queryParams("from_id");
            String to_id = request.queryParams("to_id");
            String to_comment_id = request.queryParams("to_comment_id");
            String content = request.queryParams("content");
            String comment_id = null;
            Comment comment = new Comment();
            comment.setPost_id(post_id);
            comment.setFrom_id(from_id);
            comment.setTo_id(to_id);
            comment.setTo_comment_id(to_comment_id);
            comment.setContent(content);
            while (true){
                comment_id = Functions.getRandomString(30);
                String s = String.format("Select * from public.comment where comment_id='%s'", comment_id).toString();
                System.out.println(s);
                Jdbc jdbc = new Jdbc();
                ResultSet rs = jdbc.querydata(s);
                int i = 0;
                while (rs.next())
                    i++;
                if (i==0) {
                    comment.setComment_id(comment_id);
                    break;
                }
            }
            String sql = String.format("INSERT INTO public.comment(post_id, from_id, to_id, content, time, comment_id, to_comment_id) VALUES('%s', '%s', '%s', '%s', '%tF', '%s', '%s')",
                    post_id, from_id, comment.getTo_comment_id(), content, new Date(), comment_id, comment.getTo_comment_id()).toString();
            System.out.println(sql);
            Jdbc jdbc = new Jdbc();
            jdbc.save(sql);
            Gson gson = new Gson();
            return String.format("{\"isOk\": true, \"msg\": \"评论成功\", \"comment\": %s}", gson.toJson(comment));
        });

    }

    private static void log(String s){
        System.out.println(s);
    }

    private static void enableCORS(final String origin, final String methods, final String headers) {
        before(new Filter() {
            @Override
            public void handle(Request request, Response response) {
                response.header("Access-Control-Allow-Origin", origin);
                response.header("Access-Control-Request-Method", methods);
                response.header("Access-Control-Allow-Headers", headers);
            }
        });
    }

    /**
     * 每隔十分钟更新新闻
     */
    public static void updateNews(){
        Integer cacheTime = 600000;
        Integer delay = 00000;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    List<NewsCatch> list = NewsCatch.NewsCatching();
                    for (NewsCatch element : list) {
                        Jdbc jdbc = new Jdbc();
                        Jdbc jdbc1 = new Jdbc();
                        System.out.println(element.getUrl_address());
                        Date date = new SimpleDateFormat("yyyy年MM月dd日").parse(element.getDate());
                        String sql = String.format("SELECT * FROM public.post WHERE post_id='%s'", element.getUrl_address());
                        String save = String.format("INSERT INTO public.post(post_id, user_id, time, title, content, category, source) VALUES('%s', '%s','%tF', '%s', '%s', '%s', '%s')",
                                element.getUrl_address().replaceAll("'", "''"), "Newest-Tech@gmail.com", date, element.getTitle().replaceAll("'", "''"), element.getContents().replaceAll("'", "''"), element.getCatagory().replaceAll("'", "''"), element.getSource().replaceAll("'", "''"));
                        ResultSet rs = jdbc.querydata(sql);
                        int i = 0;
                        while (rs.next())
                            i++;
                        if (i == 0){
                           jdbc1.save(save);
                            System.out.println("保存成功");
                        }
                    }
                } catch (IOException e){
                    e.printStackTrace();
                } catch (SQLException e){
                    e.printStackTrace();
                } catch (ParseException e){
                    e.getErrorOffset();
                }
            }
        }, delay, cacheTime);
    }

}